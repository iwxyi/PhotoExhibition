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
@Order(70)
public class LemonSqueezyPaymentRefundAdapter extends AbstractPaymentRefundAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.LEMON_SQUEEZY;
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
        var payload = basePayload(order, refundAmountFen, PaymentProviderType.LEMON_SQUEEZY, preview);
        payload.put("path", "/v1/orders/{order_id}/refund");
        payload.put("orderId", order.getExternalTradeNo());
        payload.put("note", "建议结合控制台或后续官方 API");
        return PaymentRefundService.RefundPreviewResult.builder()
            .httpMethod("POST")
            .launchUrl(safeString(settings.getApiBaseUrl(), "https://api.lemonsqueezy.com") + "/v1/orders/" + safeString(order.getExternalTradeNo(), "{order_id}") + "/refund")
            .headers(headers)
            .payload(payload)
            .integrationSteps(List.of("确认是否走控制台或后续官方 API", "记录外部退款流水", "补 webhook 映射", "同步订单退款状态"))
            .message("该平台退款常涉及控制台或订阅/订单体系，当前已生成退款骨架，后续可按 adjustment / dashboard 流程接入。")
            .build();
    }
}
