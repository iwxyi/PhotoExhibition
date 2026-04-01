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
@Order(10)
public class AlipayPaymentRefundAdapter extends AbstractPaymentRefundAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.ALIPAY;
    }

    @Override
    public PaymentRefundService.RefundPreviewResult preview(UserPlanOrder order,
                                                            VipPlan plan,
                                                            UserAccount user,
                                                            int refundAmountFen,
                                                            PaymentConfigService.PaymentResolvedSettings settings,
                                                            PaymentGatewayService.PaymentPreview preview) {
        Map<String, Object> headers = new LinkedHashMap<>();
        Map<String, Object> bizContent = new LinkedHashMap<>();
        bizContent.put("out_trade_no", order.getOrderNo());
        bizContent.put("trade_no", order.getExternalTradeNo());
        bizContent.put("refund_amount", fenToYuan(refundAmountFen));
        bizContent.put("refund_reason", "SUPER_ADMIN_PREVIEW");
        bizContent.put("out_request_no", order.getOrderNo() + "-R");
        Map<String, Object> payload = basePayload(order, refundAmountFen, PaymentProviderType.ALIPAY, preview);
        payload.put("method", "alipay.trade.refund");
        payload.put("appId", settings.getAppId());
        payload.put("signType", "RSA2");
        payload.put("charset", "UTF-8");
        payload.put("bizContent", bizContent);
        String signingContent = buildSigningContent(payload);
        payload.put("signingContent", signingContent);
        payload.put("signatureField", "sign");
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        if (canSignWithPrivateKey(settings.getPrivateKey())) {
            payload.put("signatureReady", true);
            payload.put("signaturePreview", signSha256WithRsaBase64(settings.getPrivateKey(), signingContent));
        } else {
            payload.put("signatureReady", false);
        }
        return PaymentRefundService.RefundPreviewResult.builder()
            .httpMethod("POST")
            .launchUrl(safeString(settings.getApiBaseUrl(), "https://openapi.alipay.com/gateway.do"))
            .headers(headers)
            .payload(payload)
            .integrationSteps(List.of("组装 alipay.trade.refund 请求", "补应用私钥 RSA2 签名", "按 out_trade_no / trade_no 发起退款", "查询退款结果并回写订单"))
            .message(Boolean.TRUE.equals(payload.get("signatureReady"))
                ? "已生成更接近真实支付宝退款的请求骨架，并附带 RSA2 签名预览。"
                : "已生成更接近真实支付宝退款的请求骨架，包含 biz_content 与 out_request_no。")
            .build();
    }

    private String buildSigningContent(Map<String, Object> payload) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (entry.getValue() == null
                || "signatureReady".equals(entry.getKey())
                || "signaturePreview".equals(entry.getKey())
                || "signatureField".equals(entry.getKey())
                || "signingContent".equals(entry.getKey())) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }
}
