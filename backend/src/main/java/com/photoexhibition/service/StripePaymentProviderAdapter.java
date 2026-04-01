package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.entity.VipPlan;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(30)
public class StripePaymentProviderAdapter extends AbstractPaymentProviderAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.STRIPE;
    }

    @Override
    public PaymentInitiationService.PaymentInitiationResult initiate(UserPlanOrder order,
                                                                     VipPlan plan,
                                                                     UserAccount user,
                                                                     PaymentConfigService.PaymentResolvedSettings settings,
                                                                     PaymentGatewayService.PaymentPreview preview) {
        Map<String, Object> payload = baseLaunchPayload(order, plan, PaymentProviderType.STRIPE, preview);
        Map<String, Object> requestBody = new LinkedHashMap<>();
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + maskKey(settings.getPrivateKey()));
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("Idempotency-Key", "vip-order-" + order.getOrderNo());
        requestBody.put("mode", "payment");
        requestBody.put("success_url", settings.getReturnUrl());
        requestBody.put("cancel_url", settings.getReturnUrl());
        requestBody.put("client_reference_id", order.getOrderNo());
        requestBody.put("customer_email", user.getEmail());
        requestBody.put("metadata", Map.of(
            "orderNo", order.getOrderNo(),
            "userId", user.getId(),
            "userSlug", user.getSlug()
        ));
        requestBody.put("line_items", List.of(Map.of(
            "price_data", Map.of(
                "currency", preview.getCurrency() == null ? "cny" : preview.getCurrency().toLowerCase(),
                "unit_amount", order.getAmountFen(),
                "product_data", Map.of("name", plan.getName())
            ),
            "quantity", 1
        )));
        requestBody.put("allow_promotion_codes", true);
        payload.putAll(requestBody);
        payload.put("customerReference", user.getId());
        payload.put("requestContentType", "application/x-www-form-urlencoded");
        payload.put("requestBodyForm", requestBody);
        payload.put("requestBodyEncoded", toFormUrlEncoded(requestBody));
        payload.put("authorizationScheme", "Bearer");
        payload.put("idempotencyKey", "vip-order-" + order.getOrderNo());

        return PaymentInitiationService.PaymentInitiationResult.builder()
            .providerType(PaymentProviderType.STRIPE.name())
            .providerLabel(preview.getProviderLabel())
            .orderNo(order.getOrderNo())
            .httpMethod("POST")
            .launchUrl(preview.getApiBaseUrl() + "/v1/checkout/sessions")
            .redirect(false)
            .actionType("API_REQUEST")
            .mockMode(preview.isMockEnabled())
            .liveModeReady(preview.isLiveModeReady())
            .message("Stripe Checkout 适配入口已生成，现已补齐更接近真实请求的 form-urlencoded Body 与幂等键预览。")
            .headers(headers)
            .payload(payload)
            .build();
    }
}
