package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.entity.VipPlan;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(30)
public class StripePaymentRefundAdapter extends AbstractPaymentRefundAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.STRIPE;
    }

    @Override
    public PaymentRefundService.RefundPreviewResult preview(UserPlanOrder order,
                                                            VipPlan plan,
                                                            UserAccount user,
                                                            int refundAmountFen,
                                                            PaymentConfigService.PaymentResolvedSettings settings,
                                                            PaymentGatewayService.PaymentPreview preview) {
        Map<String, Object> headers = new LinkedHashMap<>();
        Map<String, Object> requestBody = new LinkedHashMap<>();
        String requestId = "refund-" + order.getOrderNo();
        headers.put("Authorization", "Bearer " + maskKey(settings.getPrivateKey()));
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("Idempotency-Key", requestId);
        var payload = basePayload(order, refundAmountFen, PaymentProviderType.STRIPE, preview);
        payload.put("path", "/v1/refunds");
        requestBody.put("payment_intent", order.getExternalTradeNo());
        requestBody.put("amount", refundAmountFen);
        requestBody.put("reason", "requested_by_customer");
        requestBody.put("metadata", Map.of(
            "orderNo", order.getOrderNo(),
            "userId", user.getId(),
            "vipPlanCode", safeString(plan.getCode(), plan.getName())
        ));
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            requestBody.put("instructions_email", user.getEmail());
        }
        payload.putAll(requestBody);
        payload.put("requestContentType", "application/x-www-form-urlencoded");
        payload.put("requestBodyForm", requestBody);
        payload.put("requestBodyEncoded", toFormUrlEncoded(requestBody));
        payload.put("requestId", requestId);
        return PaymentRefundService.RefundPreviewResult.builder()
            .httpMethod("POST")
            .launchUrl(safeString(settings.getApiBaseUrl(), "https://api.stripe.com") + "/v1/refunds")
            .headers(headers)
            .payload(payload)
            .integrationSteps(List.of("关联 payment_intent 或 charge", "调用 refunds.create", "记录 refund id", "同步退款状态到订单"))
            .message("已生成更接近真实 Stripe refunds.create 的请求骨架，包含 Idempotency-Key 与表单请求体预览。")
            .build();
    }
}
