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
@Order(90)
public class MolliePaymentRefundAdapter extends AbstractPaymentRefundAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.MOLLIE;
    }

    @Override
    public PaymentRefundService.RefundPreviewResult preview(UserPlanOrder order,
                                                            VipPlan plan,
                                                            UserAccount user,
                                                            int refundAmountFen,
                                                            PaymentConfigService.PaymentResolvedSettings settings,
                                                            PaymentGatewayService.PaymentPreview preview) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + maskKey(settings.getPrivateKey()));
        var payload = basePayload(order, refundAmountFen, PaymentProviderType.MOLLIE, preview);
        payload.put("path", "/v2/payments/{payment_id}/refunds");
        payload.put("paymentId", order.getExternalTradeNo());
        payload.put("amount", Map.of(
            "currency", safeString(settings.getCurrency(), "CNY"),
            "value", fenToYuan(refundAmountFen)
        ));
        payload.put("description", "SUPER_ADMIN_PREVIEW");
        payload.put("metadata", Map.of(
            "orderNo", order.getOrderNo(),
            "userId", user.getId()
        ));
        return PaymentRefundService.RefundPreviewResult.builder()
            .httpMethod("POST")
            .launchUrl(safeString(settings.getApiBaseUrl(), "https://api.mollie.com") + "/v2/payments/" + safeString(order.getExternalTradeNo(), "{payment_id}") + "/refunds")
            .headers(headers)
            .payload(payload)
            .integrationSteps(List.of("针对 payment 发起 refunds", "记录 refund id", "主动查询或接 webhook", "同步订单退款状态"))
            .message("已生成更接近真实 Mollie refunds.create 的请求骨架，包含 description 与 metadata。")
            .build();
    }
}
