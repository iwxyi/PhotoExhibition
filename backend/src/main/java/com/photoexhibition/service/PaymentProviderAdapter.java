package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.entity.VipPlan;

public interface PaymentProviderAdapter {

    boolean supports(PaymentProviderType providerType);

    PaymentInitiationService.PaymentInitiationResult initiate(UserPlanOrder order,
                                                              VipPlan plan,
                                                              UserAccount user,
                                                              PaymentConfigService.PaymentResolvedSettings settings,
                                                              PaymentGatewayService.PaymentPreview preview);
}
