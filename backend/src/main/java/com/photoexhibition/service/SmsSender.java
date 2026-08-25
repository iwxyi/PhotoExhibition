package com.photoexhibition.service;

public interface SmsSender {

    SmsProviderType getProviderType();

    SmsSenderService.SmsSendResult sendLoginCode(String phone,
                                                 String code,
                                                 SmsConfigService.SmsResolvedSettings settings);
}
