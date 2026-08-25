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
@Order(50)
public class UnionPayPaymentRefundAdapter extends AbstractPaymentRefundAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.UNIONPAY;
    }

    @Override
    public PaymentRefundService.RefundPreviewResult preview(UserPlanOrder order,
                                                            VipPlan plan,
                                                            UserAccount user,
                                                            int refundAmountFen,
                                                            PaymentConfigService.PaymentResolvedSettings settings,
                                                            PaymentGatewayService.PaymentPreview preview) {
        Map<String, Object> headers = new LinkedHashMap<>();
        Map<String, Object> payload = basePayload(order, refundAmountFen, PaymentProviderType.UNIONPAY, preview);
        payload.put("path", "/gateway/api/backTransReq.do");
        payload.put("version", "5.1.0");
        payload.put("encoding", "UTF-8");
        payload.put("txnType", "04");
        payload.put("txnSubType", "00");
        payload.put("bizType", "000201");
        payload.put("accessType", "0");
        payload.put("merId", settings.getMerchantId());
        payload.put("orderId", order.getOrderNo() + "R");
        payload.put("origQryId", order.getExternalTradeNo());
        payload.put("txnAmt", refundAmountFen);
        payload.put("backUrl", settings.getNotifyUrl());
        payload.put("signatureHint", "请按银联证书私钥生成 signature");
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        return PaymentRefundService.RefundPreviewResult.builder()
            .httpMethod("POST")
            .launchUrl(safeString(settings.getApiBaseUrl(), "https://gateway.95516.com") + "/gateway/api/backTransReq.do")
            .headers(headers)
            .payload(payload)
            .integrationSteps(List.of("准备原交易查询号 origQryId", "按银联证书签名退款请求", "提交后台交易接口", "查询退款结果后回写"))
            .message("已生成更接近真实银联后台退款的请求骨架，包含 merId / backUrl / 签名提示。")
            .build();
    }
}
