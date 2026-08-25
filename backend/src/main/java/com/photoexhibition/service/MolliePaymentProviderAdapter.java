package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.entity.VipPlan;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(90)
public class MolliePaymentProviderAdapter extends AbstractPaymentProviderAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.MOLLIE;
    }

    @Override
    public PaymentInitiationService.PaymentInitiationResult initiate(UserPlanOrder order,
                                                                     VipPlan plan,
                                                                     UserAccount user,
                                                                     PaymentConfigService.PaymentResolvedSettings settings,
                                                                     PaymentGatewayService.PaymentPreview preview) {
        String trackedReturnUrl = buildTrackedReturnUrl(settings.getReturnUrl(), PaymentProviderType.MOLLIE, order);
        Map<String, Object> payload = baseLaunchPayload(order, plan, PaymentProviderType.MOLLIE, preview);
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + maskKey(settings.getPrivateKey()));
        headers.put("Content-Type", "application/json");
        payload.put("redirectUrl", trackedReturnUrl);
        payload.put("webhookUrl", settings.getNotifyUrl());
        payload.put("sequenceType", "oneoff");
        payload.put("metadata", Map.of("orderNo", order.getOrderNo(), "userId", user.getId()));

        return PaymentInitiationService.PaymentInitiationResult.builder()
            .providerType(PaymentProviderType.MOLLIE.name())
            .providerLabel(preview.getProviderLabel())
            .orderNo(order.getOrderNo())
            .httpMethod("POST")
            .launchUrl(preview.getApiBaseUrl() + "/v2/payments")
            .redirect(false)
            .actionType("API_REQUEST")
            .mockMode(preview.isMockEnabled())
            .liveModeReady(preview.isLiveModeReady())
            .message("Mollie 适配入口已生成，可继续创建 payment 并取出 checkoutUrl 给前端跳转。")
            .headers(headers)
            .payload(payload)
            .build();
    }
}
