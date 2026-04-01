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
