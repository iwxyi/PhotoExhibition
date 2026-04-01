package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.entity.VipPlan;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(60)
public class PaddlePaymentProviderAdapter extends AbstractPaymentProviderAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.PADDLE;
    }

    @Override
    public PaymentInitiationService.PaymentInitiationResult initiate(UserPlanOrder order,
                                                                     VipPlan plan,
                                                                     UserAccount user,
                                                                     PaymentConfigService.PaymentResolvedSettings settings,
                                                                     PaymentGatewayService.PaymentPreview preview) {
        Map<String, Object> payload = baseLaunchPayload(order, plan, PaymentProviderType.PADDLE, preview);
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + maskKey(settings.getPrivateKey()));
        headers.put("Content-Type", "application/json");
        payload.put("customerEmail", user.getUsername());
        payload.put("customData", Map.of("orderNo", order.getOrderNo(), "userId", user.getId()));
        payload.put("webhookUrl", settings.getNotifyUrl());
        payload.put("returnUrl", settings.getReturnUrl());

        return PaymentInitiationService.PaymentInitiationResult.builder()
            .providerType(PaymentProviderType.PADDLE.name())
            .providerLabel(preview.getProviderLabel())
            .orderNo(order.getOrderNo())
            .httpMethod("POST")
            .launchUrl(preview.getApiBaseUrl() + "/transactions")
            .redirect(false)
            .actionType("API_REQUEST")
            .mockMode(preview.isMockEnabled())
            .liveModeReady(preview.isLiveModeReady())
            .message("Paddle 适配入口已生成，可继续创建 transaction 并回填 hosted checkout 链接。")
            .headers(headers)
            .payload(payload)
            .build();
    }
}
