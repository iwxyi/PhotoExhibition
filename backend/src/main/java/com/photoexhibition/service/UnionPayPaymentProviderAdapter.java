package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.entity.VipPlan;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(50)
public class UnionPayPaymentProviderAdapter extends AbstractPaymentProviderAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.UNIONPAY;
    }

    @Override
    public PaymentInitiationService.PaymentInitiationResult initiate(UserPlanOrder order,
                                                                     VipPlan plan,
                                                                     UserAccount user,
                                                                     PaymentConfigService.PaymentResolvedSettings settings,
                                                                     PaymentGatewayService.PaymentPreview preview) {
        Map<String, Object> payload = baseLaunchPayload(order, plan, PaymentProviderType.UNIONPAY, preview);
        Map<String, Object> formFields = new LinkedHashMap<>();
        formFields.put("version", "5.1.0");
        formFields.put("encoding", "UTF-8");
        formFields.put("txnType", "01");
        formFields.put("txnSubType", "01");
        formFields.put("bizType", "000201");
        formFields.put("channelType", "07");
        formFields.put("accessType", "0");
        formFields.put("merId", settings.getMerchantId());
        formFields.put("orderId", order.getOrderNo());
        formFields.put("txnAmt", order.getAmountFen());
        formFields.put("currencyCode", "156");
        formFields.put("frontUrl", settings.getReturnUrl());
        formFields.put("backUrl", settings.getNotifyUrl());
        formFields.put("signatureHint", "请按银联 SDK / 证书私钥生成 signature");
        payload.put("frontUrl", settings.getReturnUrl());
        payload.put("backUrl", settings.getNotifyUrl());

        return PaymentInitiationService.PaymentInitiationResult.builder()
            .providerType(PaymentProviderType.UNIONPAY.name())
            .providerLabel(preview.getProviderLabel())
            .orderNo(order.getOrderNo())
            .httpMethod("POST")
            .launchUrl(preview.getApiBaseUrl() + "/gateway/api/appTransReq.do")
            .redirect(true)
            .actionType("REDIRECT_FORM")
            .mockMode(preview.isMockEnabled())
            .liveModeReady(preview.isLiveModeReady())
            .message("银联适配入口已生成，可继续补证书签名、前台跳转与后台通知验签。")
            .formFields(formFields)
            .payload(payload)
            .build();
    }
}
