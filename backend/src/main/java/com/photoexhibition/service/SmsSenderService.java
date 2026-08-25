package com.photoexhibition.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsSenderService {

    private static final Set<SmsProviderType> GENERIC_WEBHOOK_PROVIDERS = Set.of(
        SmsProviderType.HTTP_WEBHOOK,
        SmsProviderType.HUAWEI_CLOUD,
        SmsProviderType.VOLCENGINE,
        SmsProviderType.CLOOPEN,
        SmsProviderType.AWS_SNS,
        SmsProviderType.YUNPIAN,
        SmsProviderType.SUBMAIL,
        SmsProviderType.MESSAGEBIRD,
        SmsProviderType.VONAGE,
        SmsProviderType.INFOBIP,
        SmsProviderType.PLIVO,
        SmsProviderType.SINCH,
        SmsProviderType.TELNYX,
        SmsProviderType.SMSAERO
    );

    private final SmsConfigService smsConfigService;
    private final List<SmsSender> smsSenders;

    public SmsSendResult sendLoginCode(String phone, String code) {
        SmsConfigService.SmsResolvedSettings settings = smsConfigService.getResolvedSettings();
        if (!settings.isEnabled()) {
            if (settings.isMockEnabled()) {
                log.info("短信模拟发送成功: phone={}, provider={}, code={}", phone, settings.getProviderType(), code);
                return SmsSendResult.builder()
                    .success(true)
                    .providerType(settings.getProviderType() != null ? settings.getProviderType().name() : SmsProviderType.ALIYUN.name())
                    .providerMessageId("mock-" + UUID.randomUUID())
                    .rawResponse("{\"mock\":true}")
                    .debugCode(code)
                    .build();
            }
            throw new RuntimeException("短信服务未启用");
        }

        SmsProviderType providerType = settings.getProviderType() != null ? settings.getProviderType() : SmsProviderType.ALIYUN;
        SmsSender sender = smsSenders.stream()
            .filter(item -> item.getProviderType() == providerType)
            .findFirst()
            .orElseGet(() -> resolveGenericWebhookSender(providerType));

        SmsSendResult result = sender.sendLoginCode(phone, code, settings);
        result.setProviderType(providerType.name());
        return result;
    }

    private SmsSender resolveGenericWebhookSender(SmsProviderType providerType) {
        if (!GENERIC_WEBHOOK_PROVIDERS.contains(providerType)) {
            throw new RuntimeException("TODO: 当前短信平台暂不支持，请优先使用 ALIYUN / TENCENT_CLOUD / TWILIO 或 HTTP_WEBHOOK 骨架: " + providerType);
        }
        return smsSenders.stream()
            .filter(item -> item.getProviderType() == SmsProviderType.HTTP_WEBHOOK)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("缺少通用 HTTP 短信发送适配器"));
    }

    @Data
    @Builder
    public static class SmsSendResult {
        private boolean success;
        private String providerType;
        private String providerMessageId;
        private String rawResponse;
        private String debugCode;
    }
}
