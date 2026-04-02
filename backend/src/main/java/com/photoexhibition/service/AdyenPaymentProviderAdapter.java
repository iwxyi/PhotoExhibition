package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.entity.VipPlan;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(80)
public class AdyenPaymentProviderAdapter extends AbstractPaymentProviderAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.ADYEN;
    }

    @Override
    public PaymentInitiationService.PaymentInitiationResult initiate(UserPlanOrder order,
                                                                     VipPlan plan,
                                                                     UserAccount user,
                                                                     PaymentConfigService.PaymentResolvedSettings settings,
                                                                     PaymentGatewayService.PaymentPreview preview) {
        String trackedReturnUrl = buildTrackedReturnUrl(settings.getReturnUrl(), PaymentProviderType.ADYEN, order);
        Map<String, Object> payload = baseLaunchPayload(order, plan, PaymentProviderType.ADYEN, preview);
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("X-API-Key", maskKey(settings.getPrivateKey()));
        headers.put("Content-Type", "application/json");
        payload.put("merchantAccount", settings.getMerchantId());
        payload.put("reference", order.getOrderNo());
        payload.put("shopperReference", user.getId());
        payload.put("returnUrl", trackedReturnUrl);
        payload.put("notificationUrl", settings.getNotifyUrl());

        return PaymentInitiationService.PaymentInitiationResult.builder()
            .providerType(PaymentProviderType.ADYEN.name())
            .providerLabel(preview.getProviderLabel())
            .orderNo(order.getOrderNo())
            .httpMethod("POST")
            .launchUrl(preview.getApiBaseUrl() + "/checkout/v70/payments")
            .redirect(false)
            .actionType("API_REQUEST")
            .mockMode(preview.isMockEnabled())
            .liveModeReady(preview.isLiveModeReady())
            .message("Adyen 适配入口已生成，可继续补 paymentMethods / payments 与 3DS 跳转流程。")
            .headers(headers)
            .payload(payload)
            .build();
    }
}
