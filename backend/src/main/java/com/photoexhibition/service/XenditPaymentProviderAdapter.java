package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.entity.VipPlan;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(100)
public class XenditPaymentProviderAdapter extends AbstractPaymentProviderAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.XENDIT;
    }

    @Override
    public PaymentInitiationService.PaymentInitiationResult initiate(UserPlanOrder order,
                                                                     VipPlan plan,
                                                                     UserAccount user,
                                                                     PaymentConfigService.PaymentResolvedSettings settings,
                                                                     PaymentGatewayService.PaymentPreview preview) {
        String trackedReturnUrl = buildTrackedReturnUrl(settings.getReturnUrl(), PaymentProviderType.XENDIT, order);
        Map<String, Object> payload = baseLaunchPayload(order, plan, PaymentProviderType.XENDIT, preview);
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Basic <XENDIT_API_KEY>");
        headers.put("Content-Type", "application/json");
        payload.put("referenceId", order.getOrderNo());
        payload.put("customerId", user.getId());
        payload.put("successReturnUrl", trackedReturnUrl);
        payload.put("failureReturnUrl", trackedReturnUrl);
        payload.put("webhookSecretHint", maskKey(settings.getWebhookSecret()));

        return PaymentInitiationService.PaymentInitiationResult.builder()
            .providerType(PaymentProviderType.XENDIT.name())
            .providerLabel(preview.getProviderLabel())
            .orderNo(order.getOrderNo())
            .httpMethod("POST")
            .launchUrl(preview.getApiBaseUrl() + "/payment_requests")
            .redirect(false)
            .actionType("API_REQUEST")
            .mockMode(preview.isMockEnabled())
            .liveModeReady(preview.isLiveModeReady())
            .message("Xendit 适配入口已生成，可继续创建 payment request 并拉起 hosted payment page。")
            .headers(headers)
            .payload(payload)
            .build();
    }
}
