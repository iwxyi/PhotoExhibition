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
@Order(100)
public class XenditPaymentRefundAdapter extends AbstractPaymentRefundAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.XENDIT;
    }

    @Override
    public PaymentRefundService.RefundPreviewResult preview(UserPlanOrder order,
                                                            VipPlan plan,
                                                            UserAccount user,
                                                            int refundAmountFen,
                                                            PaymentConfigService.PaymentResolvedSettings settings,
                                                            PaymentGatewayService.PaymentPreview preview) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Basic <XENDIT_API_KEY>");
        headers.put("Content-Type", "application/json");
        Map<String, Object> payload = basePayload(order, refundAmountFen, PaymentProviderType.XENDIT, preview);
        payload.put("path", "/refunds");
        payload.put("paymentRequestId", order.getExternalTradeNo());
        payload.put("amount", fenToYuan(refundAmountFen));
        payload.put("reason", "REQUESTED_BY_CUSTOMER");
        payload.put("currency", safeString(settings.getCurrency(), "CNY"));
        payload.put("reference_id", order.getOrderNo() + "-R");
        payload.put("metadata", Map.of(
            "orderNo", order.getOrderNo(),
            "userId", user.getId()
        ));
        return PaymentRefundService.RefundPreviewResult.builder()
            .httpMethod("POST")
            .launchUrl(safeString(settings.getApiBaseUrl(), "https://api.xendit.co") + "/refunds")
            .headers(headers)
            .payload(payload)
            .integrationSteps(List.of("调用 refunds API", "记录 refund id", "校验 webhook", "同步订单退款状态"))
            .message("已生成更接近真实 Xendit refunds.create 的请求骨架，包含 reference_id 与 metadata。")
            .build();
    }
}
