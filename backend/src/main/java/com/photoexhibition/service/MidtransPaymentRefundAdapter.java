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
@Order(110)
public class MidtransPaymentRefundAdapter extends AbstractPaymentRefundAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.MIDTRANS;
    }

    @Override
    public PaymentRefundService.RefundPreviewResult preview(UserPlanOrder order,
                                                            VipPlan plan,
                                                            UserAccount user,
                                                            int refundAmountFen,
                                                            PaymentConfigService.PaymentResolvedSettings settings,
                                                            PaymentGatewayService.PaymentPreview preview) {
        var headers = new LinkedHashMap<String, Object>();
        headers.put("Authorization", "Basic <MIDTRANS_SERVER_KEY>");
        headers.put("Content-Type", "application/json");
        var payload = basePayload(order, refundAmountFen, PaymentProviderType.MIDTRANS, preview);
        payload.put("path", "/v2/{order_id}/refund");
        payload.put("orderId", order.getOrderNo());
        payload.put("amount", fenToYuan(refundAmountFen));
        payload.put("reason", "SUPER_ADMIN_PREVIEW");
        payload.put("refund_key", order.getOrderNo() + "-R");
        payload.put("additionalInfo", Map.of(
            "userId", user.getId(),
            "vipPlanCode", safeString(plan.getCode(), plan.getName())
        ));
        return PaymentRefundService.RefundPreviewResult.builder()
            .httpMethod("POST")
            .launchUrl(safeString(settings.getApiBaseUrl(), "https://api.midtrans.com") + "/v2/" + order.getOrderNo() + "/refund")
            .headers(headers)
            .payload(payload)
            .integrationSteps(List.of("调用订单退款 API", "记录 refund_key / refund id", "查询退款状态", "同步订单退款状态"))
            .message("已生成更接近真实 Midtrans refund 的请求骨架，包含 refund_key 与附加信息。")
            .build();
    }
}
