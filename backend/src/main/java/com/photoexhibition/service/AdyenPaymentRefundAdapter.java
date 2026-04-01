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
@Order(80)
public class AdyenPaymentRefundAdapter extends AbstractPaymentRefundAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.ADYEN;
    }

    @Override
    public PaymentRefundService.RefundPreviewResult preview(UserPlanOrder order,
                                                            VipPlan plan,
                                                            UserAccount user,
                                                            int refundAmountFen,
                                                            PaymentConfigService.PaymentResolvedSettings settings,
                                                            PaymentGatewayService.PaymentPreview preview) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("X-API-Key", maskKey(settings.getPrivateKey()));
        Map<String, Object> payload = basePayload(order, refundAmountFen, PaymentProviderType.ADYEN, preview);
        payload.put("path", "/pal/servlet/Payment/v68/refund");
        payload.put("originalReference", order.getExternalTradeNo());
        payload.put("merchantAccount", settings.getMerchantId());
        payload.put("reference", order.getOrderNo() + "-R");
        payload.put("modificationAmount", Map.of(
            "currency", safeString(settings.getCurrency(), "CNY"),
            "value", refundAmountFen
        ));
        payload.put("metadata", Map.of(
            "orderNo", order.getOrderNo(),
            "userId", user.getId(),
            "vipPlanCode", safeString(plan.getCode(), plan.getName())
        ));
        return PaymentRefundService.RefundPreviewResult.builder()
            .httpMethod("POST")
            .launchUrl(safeString(settings.getApiBaseUrl(), "https://checkout-live.adyen.com") + "/pal/servlet/Payment/v68/refund")
            .headers(headers)
            .payload(payload)
            .integrationSteps(List.of("使用 originalReference 发起 refund", "记录 pspReference", "接收 webhook 退款结果", "同步订单退款状态"))
            .message("已生成更接近真实 Adyen refund 的请求骨架，包含 reference 与 metadata。")
            .build();
    }
}
