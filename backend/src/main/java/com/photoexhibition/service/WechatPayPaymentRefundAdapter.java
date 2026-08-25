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
@Order(20)
public class WechatPayPaymentRefundAdapter extends AbstractPaymentRefundAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.WECHAT_PAY;
    }

    @Override
    public PaymentRefundService.RefundPreviewResult preview(UserPlanOrder order,
                                                            VipPlan plan,
                                                            UserAccount user,
                                                            int refundAmountFen,
                                                            PaymentConfigService.PaymentResolvedSettings settings,
                                                            PaymentGatewayService.PaymentPreview preview) {
        var headers = new LinkedHashMap<String, Object>();
        var payload = basePayload(order, refundAmountFen, PaymentProviderType.WECHAT_PAY, preview);
        var requestBody = new LinkedHashMap<String, Object>();
        requestBody.put("out_trade_no", order.getOrderNo());
        requestBody.put("out_refund_no", order.getOrderNo() + "-R");
        requestBody.put("reason", "SUPER_ADMIN_PREVIEW");
        requestBody.put("notify_url", settings.getNotifyUrl());
        requestBody.put("amount", Map.of(
            "refund", refundAmountFen,
            "total", order.getAmountFen(),
            "currency", safeString(settings.getCurrency(), "CNY")
        ));
        String path = "/v3/refund/domestic/refunds";
        String timestamp = resolveEpochSeconds(order);
        String nonce = "refund_" + order.getId() + "_" + user.getId();
        String requestBodyJson = toJson(requestBody);
        String signingMessage = "POST\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + requestBodyJson + "\n";
        headers.put("Authorization", "WECHATPAY2-SHA256-RSA2048 <待签名>");
        headers.put("Wechatpay-Serial", settings.getCertificateSerialNo());
        headers.put("Content-Type", "application/json");
        payload.put("path", path);
        payload.putAll(requestBody);
        payload.put("requestBodyJson", requestBodyJson);
        payload.put("signingMessage", signingMessage);
        payload.put("signatureField", "Authorization");
        payload.put("signatureAlgorithm", "WECHATPAY2-SHA256-RSA2048");
        payload.put("signingTimestamp", timestamp);
        payload.put("signingNonce", nonce);
        if (canSignWithPrivateKey(settings.getPrivateKey())) {
            String signature = signSha256WithRsaBase64(settings.getPrivateKey(), signingMessage);
            String authorization = "WECHATPAY2-SHA256-RSA2048 mchid=\"" + settings.getMerchantId()
                + "\",nonce_str=\"" + nonce
                + "\",signature=\"" + signature
                + "\",timestamp=\"" + timestamp
                + "\",serial_no=\"" + settings.getCertificateSerialNo() + "\"";
            headers.put("Authorization", authorization);
            payload.put("signatureReady", true);
            payload.put("signaturePreview", signature);
            payload.put("authorizationPreview", authorization);
        } else {
            payload.put("signatureReady", false);
        }
        return PaymentRefundService.RefundPreviewResult.builder()
            .httpMethod("POST")
            .launchUrl(safeString(settings.getApiBaseUrl(), "https://api.mch.weixin.qq.com") + "/v3/refund/domestic/refunds")
            .headers(headers)
            .payload(payload)
            .integrationSteps(List.of("构造 v3 退款请求体", "按商户私钥签名请求", "记录 out_refund_no 与微信退款单号", "回调或查询后回写订单"))
            .message(Boolean.TRUE.equals(payload.get("signatureReady"))
                ? "已生成更接近真实微信支付退款的请求骨架，并附带 Authorization 签名预览。"
                : "已生成更接近真实微信支付退款的请求骨架，包含 notify_url 与退款流水号。")
            .build();
    }
}
