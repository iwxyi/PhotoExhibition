package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.entity.VipPlan;
import com.photoexhibition.repository.UserAccountRepository;
import com.photoexhibition.repository.UserPlanOrderRepository;
import com.photoexhibition.repository.VipPlanRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentRefundService {

    private final PaymentConfigService paymentConfigService;
    private final PaymentGatewayService paymentGatewayService;
    private final UserPlanOrderRepository userPlanOrderRepository;
    private final UserAccountRepository userAccountRepository;
    private final VipPlanRepository vipPlanRepository;
    private final List<PaymentRefundAdapter> paymentRefundAdapters;

    /**
     * Executes the Alipay refund from the server.  Browser clients never receive the private key.
     */
    public Map<String, Object> executeAlipayRefund(UserPlanOrder order, int refundAmountFen) {
        PaymentConfigService.PaymentResolvedSettings settings = paymentConfigService.getResolvedSettings();
        if (settings.isMockEnabled()) {
            throw new RuntimeException("Mock 模式下不调用支付宝退款接口");
        }
        if (!settings.isEnabled() || settings.getProviderType() != PaymentProviderType.ALIPAY) {
            throw new RuntimeException("当前不是已启用的支付宝支付配置");
        }
        if (order == null || order.getExternalTradeNo() == null || order.getExternalTradeNo().isBlank()) {
            throw new RuntimeException("退款需要已记录的支付宝交易号");
        }
        if (settings.getAppId() == null || settings.getAppId().isBlank() || settings.getPrivateKey() == null || settings.getPrivateKey().isBlank()) {
            throw new RuntimeException("支付宝退款配置不完整");
        }

        Map<String, Object> biz = new LinkedHashMap<>();
        biz.put("out_trade_no", order.getOrderNo());
        biz.put("trade_no", order.getExternalTradeNo());
        biz.put("refund_amount", fenToYuan(refundAmountFen));
        biz.put("out_request_no", order.getOrderNo() + "-R" + System.currentTimeMillis());

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("app_id", settings.getAppId());
        fields.put("method", "alipay.trade.refund");
        fields.put("charset", "UTF-8");
        fields.put("sign_type", "RSA2");
        fields.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        fields.put("version", "1.0");
        fields.put("biz_content", toJson(biz));
        fields.put("sign", sign(settings.getPrivateKey(), signingContent(fields)));

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(resolveAlipayGateway(settings)))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(toForm(fields), StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("支付宝退款请求失败，HTTP " + response.statusCode());
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> root = new com.fasterxml.jackson.databind.ObjectMapper().readValue(response.body(), Map.class);
            Object raw = root.get("alipay_trade_refund_response");
            if (!(raw instanceof Map)) {
                throw new RuntimeException("支付宝退款响应格式异常");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) raw;
            if (!"10000".equals(String.valueOf(result.get("code")))) {
                throw new RuntimeException("支付宝退款失败: " + String.valueOf(result.getOrDefault("sub_msg", result.get("msg"))));
            }
            Map<String, Object> safe = new LinkedHashMap<>();
            safe.put("tradeNo", order.getExternalTradeNo());
            safe.put("outRequestNo", biz.get("out_request_no"));
            safe.put("refundFee", result.get("refund_fee"));
            safe.put("fundChange", result.get("fund_change"));
            return safe;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("调用支付宝退款接口失败", e);
        }
    }

    private String resolveAlipayGateway(PaymentConfigService.PaymentResolvedSettings settings) {
        return settings.getApiBaseUrl() == null || settings.getApiBaseUrl().isBlank()
            ? "https://openapi.alipay.com/gateway.do" : settings.getApiBaseUrl();
    }

    private String toJson(Map<String, Object> value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("支付宝退款参数序列化失败", e);
        }
    }

    private String signingContent(Map<String, String> fields) {
        List<String> keys = new ArrayList<>(fields.keySet());
        keys.remove("sign");
        keys.sort(Comparator.naturalOrder());
        List<String> pairs = new ArrayList<>();
        for (String key : keys) pairs.add(key + "=" + fields.get(key));
        return String.join("&", pairs);
    }

    private String toForm(Map<String, String> fields) {
        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            pairs.add(java.net.URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                + java.net.URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return String.join("&", pairs);
    }

    private String sign(String privateKey, String content) {
        try {
            String normalized = privateKey.replaceAll("-----BEGIN (RSA )?PRIVATE KEY-----|-----END (RSA )?PRIVATE KEY-----|\\s+", "");
            PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(normalized)));
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(key);
            signature.update(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new RuntimeException("支付宝退款签名失败", e);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> previewByOrderId(Long orderId, Integer refundAmountFen) {
        UserPlanOrder order = userPlanOrderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("VIP 订单不存在"));
        UserAccount user = userAccountRepository.findById(order.getUserId())
            .orElseThrow(() -> new RuntimeException("订单用户不存在"));
        VipPlan plan = vipPlanRepository.findById(order.getVipPlanId())
            .orElseThrow(() -> new RuntimeException("VIP 套餐不存在"));
        validateRefundable(order);

        PaymentConfigService.PaymentResolvedSettings settings = paymentConfigService.getResolvedSettings();
        PaymentGatewayService.PaymentPreview preview = paymentGatewayService.preview(order, plan, user);
        PaymentProviderType providerType = settings.getProviderType() == null ? PaymentProviderType.ALIPAY : settings.getProviderType();
        int effectiveRefundAmountFen = refundAmountFen == null || refundAmountFen <= 0
            ? (order.getAmountFen() == null ? 0 : order.getAmountFen())
            : Math.min(refundAmountFen, order.getAmountFen() == null ? refundAmountFen : order.getAmountFen());

        PaymentRefundAdapter adapter = paymentRefundAdapters.stream()
            .filter(item -> item.supports(providerType))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("未找到退款适配器: " + providerType));
        RefundPreviewResult adapterResult = adapter.preview(order, plan, user, effectiveRefundAmountFen, settings, preview);
        boolean refundReady = preview.isRefundReady();
        List<PaymentGatewayService.StageReadiness> refundStageReadiness = preview.getStageReadiness() == null
            ? List.of()
            : preview.getStageReadiness().stream()
                .filter(stage -> "refund".equals(stage.getStageKey()))
                .collect(Collectors.toList());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("orderId", order.getId());
        resp.put("orderNo", order.getOrderNo());
        resp.put("status", order.getStatus());
        resp.put("providerType", providerType.name());
        resp.put("providerLabel", preview.getProviderLabel());
        resp.put("mockMode", preview.isMockEnabled());
        resp.put("liveModeReady", refundReady);
        resp.put("refundReady", refundReady);
        resp.put("refundMode", preview.getRefundMode());
        resp.put("verificationMode", preview.getVerificationMode());
        resp.put("missingFields", preview.getMissingFields());
        resp.put("readinessWarnings", preview.getReadinessWarnings());
        resp.put("stageReadiness", refundStageReadiness);
        resp.put("recommendedConfigFields", preview.getRecommendedConfigFields());
        resp.put("nextActionHints", preview.getNextActionHints());
        resp.put("supportMessage", preview.getSupportMessage());
        resp.put("refundAmountFen", effectiveRefundAmountFen);
        resp.put("refundAmountYuan", fenToYuan(effectiveRefundAmountFen));
        resp.put("httpMethod", adapterResult.getHttpMethod());
        resp.put("launchUrl", adapterResult.getLaunchUrl());
        resp.put("headers", adapterResult.getHeaders() == null || adapterResult.getHeaders().isEmpty() ? null : adapterResult.getHeaders());
        resp.put("payload", adapterResult.getPayload());
        resp.put("capabilityTags", preview.getCapabilityTags());
        resp.put("integrationSteps", adapterResult.getIntegrationSteps());
        resp.put("message", adapterResult.getMessage());
        return resp;
    }

    private void validateRefundable(UserPlanOrder order) {
        String status = order.getStatus() == null ? "" : order.getStatus().trim().toUpperCase();
        if (!List.of("PAID", "ACTIVE", "REFUNDED").contains(status)) {
            throw new RuntimeException("当前订单状态不可退款预览");
        }
    }

    private String fenToYuan(Integer fen) {
        return BigDecimal.valueOf(fen == null ? 0 : fen)
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
            .toPlainString();
    }

    @Data
    @Builder
    public static class RefundPreviewResult {
        private String httpMethod;
        private String launchUrl;
        private Map<String, Object> headers;
        private Map<String, Object> payload;
        private List<String> integrationSteps;
        private String message;
    }
}
