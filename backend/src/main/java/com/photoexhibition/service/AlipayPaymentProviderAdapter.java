package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.entity.VipPlan;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@Order(10)
public class AlipayPaymentProviderAdapter extends AbstractPaymentProviderAdapter {

    private static final DateTimeFormatter ALIPAY_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.ALIPAY;
    }

    @Override
    public PaymentInitiationService.PaymentInitiationResult initiate(UserPlanOrder order,
                                                                     VipPlan plan,
                                                                     UserAccount user,
                                                                     PaymentConfigService.PaymentResolvedSettings settings,
                                                                     PaymentGatewayService.PaymentPreview preview) {
        String trackedReturnUrl = buildTrackedReturnUrl(settings.getReturnUrl(), PaymentProviderType.ALIPAY, order);
        Map<String, Object> payload = baseLaunchPayload(order, plan, PaymentProviderType.ALIPAY, preview);
        Map<String, Object> formFields = new LinkedHashMap<>();
        Map<String, Object> bizContent = new LinkedHashMap<>();
        bizContent.put("out_trade_no", order.getOrderNo());
        bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");
        bizContent.put("total_amount", preview.getRequestPayload().get("amountYuan"));
        bizContent.put("subject", plan.getName());
        bizContent.put("timeout_express", "15m");
        bizContent.put("quit_url", trackedReturnUrl);
        formFields.put("method", "alipay.trade.page.pay");
        formFields.put("app_id", settings.getAppId());
        formFields.put("charset", "UTF-8");
        formFields.put("sign_type", "RSA2");
        formFields.put("version", "1.0");
        // Alipay accepts a narrow timestamp window; use the actual submission time, not order creation time.
        formFields.put("timestamp", LocalDateTime.now().format(ALIPAY_TIMESTAMP));
        formFields.put("return_url", trackedReturnUrl);
        formFields.put("notify_url", settings.getNotifyUrl());
        String bizContentJson = toJson(bizContent);
        formFields.put("biz_content", bizContentJson);
        payload.put("bizContent", bizContent);
        payload.put("bizContentJson", bizContentJson);
        payload.put("gatewayPath", "/gateway.do");
        payload.put("signType", "RSA2");
        payload.put("signatureField", "sign");
        payload.put("returnUrl", trackedReturnUrl);
        payload.put("requestContentType", "application/x-www-form-urlencoded");
        payload.put("requestBodyForm", sanitizeFormFields(formFields));
        String signingContent = buildSigningContent(formFields);
        payload.put("signingContent", signingContent);
        payload.put("requestBodyEncoded", toFormUrlEncoded(sanitizeFormFields(formFields)));
        if (!canSignWithPrivateKey(settings.getPrivateKey()) && !preview.isMockEnabled()) {
            throw new RuntimeException("支付宝商户私钥未配置或格式无效，无法生成 RSA2 签名");
        }
        if (canSignWithPrivateKey(settings.getPrivateKey())) {
            String sign = signSha256WithRsaBase64(settings.getPrivateKey(), signingContent);
            formFields.put("sign", sign);
            payload.put("signatureReady", true);
        } else {
            payload.put("signatureReady", false);
        }
        payload.put("requestBodyForm", sanitizeFormFields(formFields));
        payload.put("requestBodyEncoded", toFormUrlEncoded(sanitizeFormFields(formFields)));

        return PaymentInitiationService.PaymentInitiationResult.builder()
            .providerType(PaymentProviderType.ALIPAY.name())
            .providerLabel(preview.getProviderLabel())
            .orderNo(order.getOrderNo())
            .httpMethod("POST")
            .launchUrl(withCharsetQuery(preview.getApiBaseUrl()))
            .redirect(true)
            .actionType("REDIRECT_FORM")
            .mockMode(preview.isMockEnabled())
            .liveModeReady(preview.isLiveModeReady())
            .message(Boolean.TRUE.equals(payload.get("signatureReady"))
                ? "支付宝支付表单已生成，并已使用商户私钥完成 RSA2 签名。"
                : "支付宝模拟支付表单已生成。")
            .formFields(formFields)
            .payload(payload)
            .build();
    }

    private String buildSigningContent(Map<String, Object> formFields) {
        StringBuilder builder = new StringBuilder();
        Map<String, Object> sorted = new TreeMap<>(Comparator.naturalOrder());
        sorted.putAll(formFields);
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            if (entry.getValue() == null
                || "sign".equals(entry.getKey())) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private Map<String, Object> sanitizeFormFields(Map<String, Object> formFields) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : formFields.entrySet()) {
            sanitized.put(entry.getKey(), entry.getValue());
        }
        return sanitized;
    }

    private String withCharsetQuery(String gatewayUrl) {
        if (gatewayUrl == null || gatewayUrl.isBlank()) {
            return gatewayUrl;
        }
        if (gatewayUrl.matches("(?i).*([?&])charset=[^&]*.*")) {
            return gatewayUrl;
        }
        return gatewayUrl + (gatewayUrl.contains("?") ? "&" : "?") + "charset=UTF-8";
    }
}
