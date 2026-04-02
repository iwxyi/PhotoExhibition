package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.entity.VipPlan;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(110)
public class MidtransPaymentProviderAdapter extends AbstractPaymentProviderAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.MIDTRANS;
    }

    @Override
    public PaymentInitiationService.PaymentInitiationResult initiate(UserPlanOrder order,
                                                                     VipPlan plan,
                                                                     UserAccount user,
                                                                     PaymentConfigService.PaymentResolvedSettings settings,
                                                                     PaymentGatewayService.PaymentPreview preview) {
        String trackedReturnUrl = buildTrackedReturnUrl(settings.getReturnUrl(), PaymentProviderType.MIDTRANS, order);
        Map<String, Object> payload = baseLaunchPayload(order, plan, PaymentProviderType.MIDTRANS, preview);
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Basic <MIDTRANS_SERVER_KEY>");
        headers.put("Content-Type", "application/json");
        payload.put("transactionDetails", Map.of("order_id", order.getOrderNo(), "gross_amount", preview.getRequestPayload().get("amountYuan")));
        payload.put("customerDetails", Map.of("first_name", user.getNickname() == null ? user.getUsername() : user.getNickname(), "email", user.getUsername()));
        payload.put("callbacks", Map.of("finish", trackedReturnUrl));
        payload.put("notificationUrl", settings.getNotifyUrl());

        return PaymentInitiationService.PaymentInitiationResult.builder()
            .providerType(PaymentProviderType.MIDTRANS.name())
            .providerLabel(preview.getProviderLabel())
            .orderNo(order.getOrderNo())
            .httpMethod("POST")
            .launchUrl(preview.getApiBaseUrl() + "/v2/charge")
            .redirect(false)
            .actionType("API_REQUEST")
            .mockMode(preview.isMockEnabled())
            .liveModeReady(preview.isLiveModeReady())
            .message("Midtrans 适配入口已生成，可继续补 Snap/Core API 参数并回填 redirect_url 或 token。")
            .headers(headers)
            .payload(payload)
            .build();
    }
}
