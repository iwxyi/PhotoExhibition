package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.entity.VipPlan;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(20)
public class WechatPayPaymentProviderAdapter extends AbstractPaymentProviderAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.WECHAT_PAY;
    }

    @Override
    public PaymentInitiationService.PaymentInitiationResult initiate(UserPlanOrder order,
                                                                     VipPlan plan,
                                                                     UserAccount user,
                                                                     PaymentConfigService.PaymentResolvedSettings settings,
                                                                     PaymentGatewayService.PaymentPreview preview) {
        String trackedReturnUrl = buildTrackedReturnUrl(settings.getReturnUrl(), PaymentProviderType.WECHAT_PAY, order);
        Map<String, Object> payload = baseLaunchPayload(order, plan, PaymentProviderType.WECHAT_PAY, preview);
        Map<String, Object> headers = new LinkedHashMap<>();
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("appid", settings.getAppId());
        requestBody.put("mchid", settings.getMerchantId());
        requestBody.put("description", plan.getName());
        requestBody.put("out_trade_no", order.getOrderNo());
        requestBody.put("notify_url", settings.getNotifyUrl());
        requestBody.put("amount", Map.of(
            "total", order.getAmountFen(),
            "currency", preview.getCurrency()
        ));
        requestBody.put("attach", Map.of(
            "orderNo", order.getOrderNo(),
            "userId", user.getId()
        ));
        requestBody.put("payer", Map.of(
            "openidHint", user.getId() == null ? null : ("USER-" + user.getId())
        ));
        requestBody.put("scene_info", singletonMetadata("return_url", trackedReturnUrl));
        String path = "/v3/pay/transactions/native";
        String timestamp = resolveEpochSeconds(order);
        String nonce = "preview_" + order.getId() + "_" + user.getId();
        String requestBodyJson = toJson(requestBody);
        String signingMessage = "POST\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + requestBodyJson + "\n";
        headers.put("Wechatpay-Serial", settings.getCertificateSerialNo());
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "WECHATPAY2-SHA256-RSA2048 <待签名>");
        payload.putAll(requestBody);
        payload.put("requestBodyJson", requestBodyJson);
        payload.put("signingMessage", signingMessage);
        payload.put("signatureField", "Authorization");
        payload.put("signatureAlgorithm", "WECHATPAY2-SHA256-RSA2048");
        payload.put("authorizationTemplate", "WECHATPAY2-SHA256-RSA2048 mchid=\"" + settings.getMerchantId()
            + "\",nonce_str=\"<nonce>\",signature=\"<signature>\",timestamp=\"<timestamp>\",serial_no=\""
            + settings.getCertificateSerialNo() + "\"");
        payload.put("signingPath", path);
        payload.put("signingNonce", nonce);
        payload.put("signingTimestamp", timestamp);
        payload.put("notifyUrl", settings.getNotifyUrl());
        payload.put("returnUrl", trackedReturnUrl);
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

        return PaymentInitiationService.PaymentInitiationResult.builder()
            .providerType(PaymentProviderType.WECHAT_PAY.name())
            .providerLabel(preview.getProviderLabel())
            .orderNo(order.getOrderNo())
            .httpMethod("POST")
            .launchUrl(preview.getApiBaseUrl() + "/v3/pay/transactions/native")
            .redirect(false)
            .actionType("QR_CODE")
            .mockMode(preview.isMockEnabled())
            .liveModeReady(preview.isLiveModeReady())
            .message(Boolean.TRUE.equals(payload.get("signatureReady"))
                ? "微信支付适配入口已生成，并已根据私钥生成 Authorization 签名预览。"
                : "微信支付适配入口已生成，已补齐 Native 下单主体字段与签名提示。")
            .headers(headers)
            .qrCodeText("weixin://wxpay/mock/" + order.getOrderNo())
            .payload(payload)
            .build();
    }
}
