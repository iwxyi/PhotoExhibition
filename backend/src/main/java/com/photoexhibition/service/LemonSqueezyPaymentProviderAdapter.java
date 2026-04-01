package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.entity.VipPlan;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(70)
public class LemonSqueezyPaymentProviderAdapter extends AbstractPaymentProviderAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.LEMON_SQUEEZY;
    }

    @Override
    public PaymentInitiationService.PaymentInitiationResult initiate(UserPlanOrder order,
                                                                     VipPlan plan,
                                                                     UserAccount user,
                                                                     PaymentConfigService.PaymentResolvedSettings settings,
                                                                     PaymentGatewayService.PaymentPreview preview) {
        Map<String, Object> payload = baseLaunchPayload(order, plan, PaymentProviderType.LEMON_SQUEEZY, preview);
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + maskKey(settings.getPrivateKey()));
        headers.put("Accept", "application/vnd.api+json");
        headers.put("Content-Type", "application/vnd.api+json");
        payload.put("storeId", settings.getAppId());
        payload.put("checkoutData", Map.of("custom", Map.of("orderNo", order.getOrderNo(), "userSlug", user.getSlug())));
        payload.put("redirectUrl", settings.getReturnUrl());

        return PaymentInitiationService.PaymentInitiationResult.builder()
            .providerType(PaymentProviderType.LEMON_SQUEEZY.name())
            .providerLabel(preview.getProviderLabel())
            .orderNo(order.getOrderNo())
            .httpMethod("POST")
            .launchUrl(preview.getApiBaseUrl() + "/v1/checkouts")
            .redirect(false)
            .actionType("API_REQUEST")
            .mockMode(preview.isMockEnabled())
            .liveModeReady(preview.isLiveModeReady())
            .message("Lemon Squeezy 适配入口已生成，可继续补 variant/store 映射并回填 checkout URL。")
            .headers(headers)
            .payload(payload)
            .build();
    }
}
