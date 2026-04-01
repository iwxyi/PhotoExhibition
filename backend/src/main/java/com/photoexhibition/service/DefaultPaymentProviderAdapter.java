package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.entity.VipPlan;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class DefaultPaymentProviderAdapter extends AbstractPaymentProviderAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return true;
    }

    @Override
    public PaymentInitiationService.PaymentInitiationResult initiate(UserPlanOrder order,
                                                                     VipPlan plan,
                                                                     UserAccount user,
                                                                     PaymentConfigService.PaymentResolvedSettings settings,
                                                                     PaymentGatewayService.PaymentPreview preview) {
        PaymentProviderType providerType = settings.getProviderType() == null ? PaymentProviderType.ALIPAY : settings.getProviderType();
        Map<String, Object> launchPayload = new LinkedHashMap<>();
        Map<String, Object> formFields = new LinkedHashMap<>();
        Map<String, Object> headers = new LinkedHashMap<>();
        launchPayload.put("orderNo", order.getOrderNo());
        launchPayload.put("providerType", providerType.name());
        launchPayload.put("subject", plan.getName());
        launchPayload.put("amountFen", order.getAmountFen());
        launchPayload.put("currency", preview.getCurrency());
        launchPayload.put("requestPayload", preview.getRequestPayload());

        String launchUrl;
        String method = "POST";
        boolean redirect = false;
        String actionType = "API_REQUEST";
        String message;
        String qrCodeText = null;

        switch (providerType) {
            case ALIPAY:
                launchUrl = preview.getApiBaseUrl();
                redirect = true;
                actionType = "REDIRECT_FORM";
                formFields.put("method", "alipay.trade.page.pay");
                formFields.put("app_id", settings.getAppId());
                formFields.put("return_url", settings.getReturnUrl());
                formFields.put("notify_url", settings.getNotifyUrl());
                formFields.put("out_trade_no", order.getOrderNo());
                formFields.put("subject", plan.getName());
                formFields.put("total_amount", preview.getRequestPayload().get("amountYuan"));
                formFields.put("product_code", "FAST_INSTANT_TRADE_PAY");
                message = "支付宝支付拉起描述已生成，可直接按表单跳转模型继续接签名参数。";
                break;
            case WECHAT_PAY:
                launchUrl = preview.getApiBaseUrl() + "/v3/pay/transactions/native";
                actionType = "QR_CODE";
                qrCodeText = "weixin://wxpay/mock/" + order.getOrderNo();
                headers.put("Authorization", "WECHATPAY2-SHA256-RSA2048 <待签名>");
                message = "微信支付拉起描述已生成，当前返回 Native 二维码占位文本与请求头骨架。";
                break;
            case STRIPE:
                launchUrl = preview.getApiBaseUrl() + "/v1/checkout/sessions";
                headers.put("Authorization", "Bearer " + maskKey(settings.getPrivateKey()));
                message = "Stripe Checkout 拉起描述已生成，可按该请求模型换成真实 session 创建。";
                break;
            case PAYPAL:
                launchUrl = preview.getApiBaseUrl() + "/v2/checkout/orders";
                headers.put("Authorization", "Basic <ClientId:Secret>");
                message = "PayPal 拉起描述已生成，可按该请求模型继续创建订单并取回批准链接。";
                break;
            case UNIONPAY:
                launchUrl = preview.getApiBaseUrl() + "/gateway/api/appTransReq.do";
                redirect = true;
                actionType = "REDIRECT_FORM";
                formFields.put("txnType", "01");
                formFields.put("txnSubType", "01");
                formFields.put("bizType", "000201");
                formFields.put("orderId", order.getOrderNo());
                formFields.put("txnAmt", order.getAmountFen());
                formFields.put("frontUrl", settings.getReturnUrl());
                formFields.put("backUrl", settings.getNotifyUrl());
                message = "银联拉起描述已生成，可继续补证书签名字段后直接表单跳转。";
                break;
            case PADDLE:
                launchUrl = preview.getApiBaseUrl() + "/transactions";
                headers.put("Authorization", "Bearer " + maskKey(settings.getPrivateKey()));
                message = "Paddle 拉起描述已生成，可继续补真实交易创建与 checkout 链接回填。";
                break;
            case LEMON_SQUEEZY:
                launchUrl = preview.getApiBaseUrl() + "/v1/checkouts";
                headers.put("Authorization", "Bearer " + maskKey(settings.getPrivateKey()));
                message = "Lemon Squeezy 拉起描述已生成，可继续补 Store/Variant 与 checkout URL。";
                break;
            case ADYEN:
                launchUrl = preview.getApiBaseUrl() + "/checkout/v70/payments";
                headers.put("X-API-Key", maskKey(settings.getPrivateKey()));
                message = "Adyen 拉起描述已生成，可继续接 paymentMethods + payments 流程。";
                break;
            case MOLLIE:
                launchUrl = preview.getApiBaseUrl() + "/v2/payments";
                headers.put("Authorization", "Bearer " + maskKey(settings.getPrivateKey()));
                message = "Mollie 拉起描述已生成，可继续创建 payment 并跳转 checkoutUrl。";
                break;
            case XENDIT:
                launchUrl = preview.getApiBaseUrl() + "/payment_requests";
                headers.put("Authorization", "Basic <XENDIT_API_KEY>");
                message = "Xendit 拉起描述已生成，可继续创建 payment request 并拉起 hosted payment page。";
                break;
            case MIDTRANS:
                launchUrl = preview.getApiBaseUrl() + "/v2/charge";
                headers.put("Authorization", "Basic <MIDTRANS_SERVER_KEY>");
                headers.put("Content-Type", "application/json");
                message = "Midtrans 拉起描述已生成，可继续接 Snap / Core API 并回填 redirect_url。";
                break;
            case CUSTOM_WEBHOOK:
            default:
                launchUrl = preview.getApiBaseUrl();
                message = "自定义支付网关拉起描述已生成，可直接映射到内部收银台或聚合支付接口。";
                break;
        }

        return PaymentInitiationService.PaymentInitiationResult.builder()
            .providerType(providerType.name())
            .providerLabel(preview.getProviderLabel())
            .orderNo(order.getOrderNo())
            .httpMethod(method)
            .launchUrl(launchUrl)
            .redirect(redirect)
            .actionType(actionType)
            .mockMode(preview.isMockEnabled())
            .liveModeReady(preview.isLiveModeReady())
            .message(message)
            .headers(headers.isEmpty() ? null : headers)
            .formFields(formFields.isEmpty() ? null : formFields)
            .qrCodeText(qrCodeText)
            .payload(launchPayload)
            .build();
    }
}
