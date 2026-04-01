package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.entity.VipPlan;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(40)
public class PaypalPaymentProviderAdapter extends AbstractPaymentProviderAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.PAYPAL;
    }

    @Override
    public PaymentInitiationService.PaymentInitiationResult initiate(UserPlanOrder order,
                                                                     VipPlan plan,
                                                                     UserAccount user,
                                                                     PaymentConfigService.PaymentResolvedSettings settings,
                                                                     PaymentGatewayService.PaymentPreview preview) {
        Map<String, Object> payload = baseLaunchPayload(order, plan, PaymentProviderType.PAYPAL, preview);
        Map<String, Object> headers = new LinkedHashMap<>();
        Map<String, Object> applicationContext = new LinkedHashMap<>();
        Map<String, Object> payer = new LinkedHashMap<>();
        String basicCredential = buildBasicCredential(settings.getAppId(), settings.getPrivateKey());
        headers.put("Authorization", "Basic " + basicCredential);
        headers.put("Content-Type", "application/json");
        headers.put("PayPal-Request-Id", "vip-order-" + order.getOrderNo());
        payload.put("intent", "CAPTURE");
        payload.put("returnUrl", settings.getReturnUrl());
        payload.put("cancelUrl", settings.getReturnUrl());
        payload.put("customId", order.getOrderNo());
        payload.put("purchase_units", List.of(Map.of(
            "reference_id", order.getOrderNo(),
            "description", plan.getName(),
            "custom_id", order.getOrderNo(),
            "invoice_id", order.getOrderNo(),
            "amount", Map.of(
                "currency_code", preview.getCurrency(),
                "value", preview.getRequestPayload().get("amountYuan")
            )
        )));
        applicationContext.put("return_url", settings.getReturnUrl());
        applicationContext.put("cancel_url", settings.getReturnUrl());
        applicationContext.put("user_action", "PAY_NOW");
        if (settings.getMerchantName() != null && !settings.getMerchantName().isBlank()) {
            applicationContext.put("brand_name", settings.getMerchantName());
        }
        payload.put("application_context", applicationContext);
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            payer.put("email_address", user.getEmail());
        }
        if (!payer.isEmpty()) {
            payload.put("payer", payer);
        }
        payload.put("requestContentType", "application/json");
        payload.put("requestBodyJson", toJson(payloadWithoutPreviewFields(payload)));
        payload.put("authorizationScheme", "Basic");
        payload.put("requestId", "vip-order-" + order.getOrderNo());

        return PaymentInitiationService.PaymentInitiationResult.builder()
            .providerType(PaymentProviderType.PAYPAL.name())
            .providerLabel(preview.getProviderLabel())
            .orderNo(order.getOrderNo())
            .httpMethod("POST")
            .launchUrl(preview.getApiBaseUrl() + "/v2/checkout/orders")
            .redirect(false)
            .actionType("API_REQUEST")
            .mockMode(preview.isMockEnabled())
            .liveModeReady(preview.isLiveModeReady())
            .message("PayPal 适配入口已生成，现已补齐 checkout order 主体、Basic 认证预览与 JSON Body 预览。")
            .headers(headers)
            .payload(payload)
            .build();
    }

    private String buildBasicCredential(String clientId, String clientSecret) {
        String raw = (clientId == null ? "<client_id>" : clientId) + ":" + (clientSecret == null ? "<client_secret>" : clientSecret);
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Object> payloadWithoutPreviewFields(Map<String, Object> payload) {
        Map<String, Object> copied = new LinkedHashMap<>(payload);
        copied.remove("requestContentType");
        copied.remove("requestBodyJson");
        copied.remove("authorizationScheme");
        copied.remove("requestId");
        return copied;
    }
}
