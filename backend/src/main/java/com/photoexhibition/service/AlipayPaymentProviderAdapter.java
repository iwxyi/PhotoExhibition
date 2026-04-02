package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.entity.VipPlan;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

@Component
@Order(10)
public class AlipayPaymentProviderAdapter extends AbstractPaymentProviderAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.ALIPAY;
    }

    @Override
    public PaymentInitiationService.PaymentInitiationResult initiate(UserPlanOrder order,
                                                                     VipPlan plan,
                                                                     UserAccount user,
                                                                     PaymentConfigService.PaymentResolvedSettings settings,
                                                                     PaymentGatewayService.PaymentPreview preview) {
        String trackedReturnUrl = buildTrackedReturnUrl(settings.getReturnUrl(), PaymentProviderType.ALIPAY, order);
        Map<String, Object> payload = baseLaunchPayload(order, plan, PaymentProviderType.ALIPAY, preview);
        Map<String, Object> formFields = new LinkedHashMap<>();
        Map<String, Object> bizContent = new LinkedHashMap<>();
        bizContent.put("out_trade_no", order.getOrderNo());
        bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");
        bizContent.put("total_amount", preview.getRequestPayload().get("amountYuan"));
        bizContent.put("subject", plan.getName());
        bizContent.put("timeout_express", "15m");
        bizContent.put("quit_url", trackedReturnUrl);
        formFields.put("method", "alipay.trade.page.pay");
        formFields.put("app_id", settings.getAppId());
        formFields.put("charset", "UTF-8");
        formFields.put("sign_type", "RSA2");
        formFields.put("version", "1.0");
        formFields.put("timestamp", preview.getRequestPayload().get("createdAt"));
        formFields.put("return_url", trackedReturnUrl);
        formFields.put("notify_url", settings.getNotifyUrl());
        if (user.getId() != null) {
            formFields.put("passback_params", "userId=" + user.getId());
        }
        String bizContentJson = toJson(bizContent);
        formFields.put("biz_content", bizContentJson);
        formFields.put("sign", "<RSA2_SIGNATURE>");
        formFields.put("signatureHint", "请按支付宝 RSA2 规则生成 sign");
        formFields.put("signatureField", "sign");
        payload.put("bizContent", bizContent);
        payload.put("bizContentJson", bizContentJson);
        payload.put("gatewayPath", "/gateway.do");
        payload.put("signType", "RSA2");
        payload.put("signatureField", "sign");
        payload.put("returnUrl", trackedReturnUrl);
        payload.put("requestContentType", "application/x-www-form-urlencoded");
        payload.put("requestBodyForm", sanitizeFormFields(formFields));
        String signingContent = buildSigningContent(formFields);
        payload.put("signingContent", signingContent);
        payload.put("requestBodyEncoded", toFormUrlEncoded(sanitizeFormFields(formFields)));
        if (canSignWithPrivateKey(settings.getPrivateKey())) {
            String sign = signSha256WithRsaBase64(settings.getPrivateKey(), signingContent);
            formFields.put("sign", sign);
            payload.put("signatureReady", true);
            payload.put("signaturePreview", sign);
        } else {
            payload.put("signatureReady", false);
        }

        return PaymentInitiationService.PaymentInitiationResult.builder()
            .providerType(PaymentProviderType.ALIPAY.name())
            .providerLabel(preview.getProviderLabel())
            .orderNo(order.getOrderNo())
            .httpMethod("POST")
            .launchUrl(preview.getApiBaseUrl())
            .redirect(true)
            .actionType("REDIRECT_FORM")
            .mockMode(preview.isMockEnabled())
            .liveModeReady(preview.isLiveModeReady())
            .message(Boolean.TRUE.equals(payload.get("signatureReady"))
                ? "支付宝适配入口已生成，并已根据私钥生成 RSA2 签名预览。"
                : "支付宝适配入口已生成，已补齐 page pay 表单字段与 biz_content 骨架。")
            .formFields(formFields)
            .payload(payload)
            .build();
    }

    private String buildSigningContent(Map<String, Object> formFields) {
        StringBuilder builder = new StringBuilder();
        Map<String, Object> sorted = new TreeMap<>(Comparator.naturalOrder());
        sorted.putAll(formFields);
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            if (entry.getValue() == null
                || "sign".equals(entry.getKey())
                || "signatureHint".equals(entry.getKey())
                || "signatureField".equals(entry.getKey())) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private Map<String, Object> sanitizeFormFields(Map<String, Object> formFields) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : formFields.entrySet()) {
            if ("signatureHint".equals(entry.getKey()) || "signatureField".equals(entry.getKey())) {
                continue;
            }
            sanitized.put(entry.getKey(), entry.getValue());
        }
        return sanitized;
    }
}
