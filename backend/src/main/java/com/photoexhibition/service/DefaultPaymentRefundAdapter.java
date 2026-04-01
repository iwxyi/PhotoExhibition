package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.entity.VipPlan;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class DefaultPaymentRefundAdapter extends AbstractPaymentRefundAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return true;
    }

    @Override
    public PaymentRefundService.RefundPreviewResult preview(UserPlanOrder order,
                                                            VipPlan plan,
                                                            UserAccount user,
                                                            int refundAmountFen,
                                                            PaymentConfigService.PaymentResolvedSettings settings,
                                                            PaymentGatewayService.PaymentPreview preview) {
        PaymentProviderType providerType = settings.getProviderType() == null ? PaymentProviderType.ALIPAY : settings.getProviderType();
        String apiBaseUrl = safeString(settings.getApiBaseUrl(), resolveDefaultApiBaseUrl(providerType));
        var payload = basePayload(order, refundAmountFen, providerType, preview);
        payload.put("path", "/refund");
        payload.put("body", Map.of(
            "orderNo", order.getOrderNo(),
            "externalTradeNo", safeString(order.getExternalTradeNo(), order.getOrderNo()),
            "refundAmountFen", refundAmountFen,
            "userId", user.getId(),
            "vipPlanCode", safeString(plan.getCode(), plan.getName())
        ));
        return PaymentRefundService.RefundPreviewResult.builder()
            .httpMethod("POST")
            .launchUrl(apiBaseUrl + "/refund")
            .headers(null)
            .payload(payload)
            .integrationSteps(List.of("约定自定义退款接口协议", "传 orderNo / externalTradeNo / refundAmount", "接收自定义退款回调", "同步订单退款状态"))
            .message("当前为自定义退款骨架，需由内部网关约定实际退款协议与签名规则。")
            .build();
    }

    private String resolveDefaultApiBaseUrl(PaymentProviderType providerType) {
        switch (providerType) {
            case WECHAT_PAY:
                return "https://api.mch.weixin.qq.com";
            case STRIPE:
                return "https://api.stripe.com";
            case PAYPAL:
                return "https://api-m.paypal.com";
            case UNIONPAY:
                return "https://gateway.95516.com";
            case PADDLE:
                return "https://api.paddle.com";
            case LEMON_SQUEEZY:
                return "https://api.lemonsqueezy.com";
            case ADYEN:
                return "https://checkout-live.adyen.com";
            case MOLLIE:
                return "https://api.mollie.com";
            case XENDIT:
                return "https://api.xendit.co";
            case MIDTRANS:
                return "https://api.midtrans.com";
            case CUSTOM_WEBHOOK:
                return "https://example.com/payment";
            case ALIPAY:
            default:
                return "https://openapi.alipay.com/gateway.do";
        }
    }
}
