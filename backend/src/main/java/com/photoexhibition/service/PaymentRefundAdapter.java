package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.entity.VipPlan;

public interface PaymentRefundAdapter {

    boolean supports(PaymentProviderType providerType);

    PaymentRefundService.RefundPreviewResult preview(UserPlanOrder order,
                                                     VipPlan plan,
                                                     UserAccount user,
                                                     int refundAmountFen,
                                                     PaymentConfigService.PaymentResolvedSettings settings,
                                                     PaymentGatewayService.PaymentPreview preview);
}
