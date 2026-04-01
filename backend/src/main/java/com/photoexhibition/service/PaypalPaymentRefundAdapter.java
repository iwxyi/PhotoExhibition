package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.entity.VipPlan;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(40)
public class PaypalPaymentRefundAdapter extends AbstractPaymentRefundAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.PAYPAL;
    }

    @Override
    public PaymentRefundService.RefundPreviewResult preview(UserPlanOrder order,
                                                            VipPlan plan,
                                                            UserAccount user,
                                                            int refundAmountFen,
                                                            PaymentConfigService.PaymentResolvedSettings settings,
                                                            PaymentGatewayService.PaymentPreview preview) {
        Map<String, Object> headers = new LinkedHashMap<>();
        String requestId = "refund-" + order.getOrderNo();
        String basicCredential = buildBasicCredential(settings.getAppId(), settings.getPrivateKey());
        headers.put("Authorization", "Basic " + basicCredential);
        headers.put("Content-Type", "application/json");
        headers.put("PayPal-Request-Id", requestId);
        Map<String, Object> payload = basePayload(order, refundAmountFen, PaymentProviderType.PAYPAL, preview);
        Map<String, Object> requestBody = new LinkedHashMap<>();
        payload.put("path", "/v2/payments/captures/{capture_id}/refund");
        payload.put("captureId", order.getExternalTradeNo());
        requestBody.put("amount", Map.of(
            "currency_code", safeString(settings.getCurrency(), "CNY"),
            "value", fenToYuan(refundAmountFen)
        ));
        requestBody.put("invoice_id", order.getOrderNo() + "-R");
        requestBody.put("note_to_payer", "SUPER_ADMIN_PREVIEW");
        requestBody.put("custom_id", order.getOrderNo());
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            requestBody.put("payer", Map.of("email_address", user.getEmail()));
        }
        payload.putAll(requestBody);
        payload.put("requestContentType", "application/json");
        payload.put("requestBodyJson", toJson(requestBody));
        payload.put("authorizationScheme", "Basic");
        payload.put("requestId", requestId);
        return PaymentRefundService.RefundPreviewResult.builder()
            .httpMethod("POST")
            .launchUrl(safeString(settings.getApiBaseUrl(), "https://api-m.paypal.com") + "/v2/payments/captures/" + safeString(order.getExternalTradeNo(), "{capture_id}") + "/refund")
            .headers(headers)
            .payload(payload)
            .integrationSteps(List.of("确认 capture id", "调用 capture refund API", "记录 PayPal refund id", "同步退款状态到订单"))
            .message("已生成更接近真实 PayPal capture refund 的请求骨架，包含 Basic 认证与 JSON 请求体预览。")
            .build();
    }

    private String buildBasicCredential(String clientId, String clientSecret) {
        String raw = (clientId == null ? "<client_id>" : clientId) + ":" + (clientSecret == null ? "<client_secret>" : clientSecret);
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
