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
@Order(60)
public class PaddlePaymentRefundAdapter extends AbstractPaymentRefundAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.PADDLE;
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
        Map<String, Object> payload = basePayload(order, refundAmountFen, PaymentProviderType.PADDLE, preview);
        payload.put("path", "/adjustments");
        payload.put("action", "refund");
        payload.put("transactionId", order.getExternalTradeNo());
        payload.put("reason", "退款预演");
        return PaymentRefundService.RefundPreviewResult.builder()
            .httpMethod("POST")
            .launchUrl(safeString(settings.getApiBaseUrl(), "https://api.paddle.com") + "/adjustments")
            .headers(headers)
            .payload(payload)
            .integrationSteps(List.of("创建 adjustment/refund", "记录 adjustment id", "结合 webhook 或控制台状态回写", "同步订单退款状态"))
            .message("该平台退款常涉及控制台或订阅/订单体系，当前已生成退款骨架，后续可按 adjustment / dashboard 流程接入。")
            .build();
    }
}
