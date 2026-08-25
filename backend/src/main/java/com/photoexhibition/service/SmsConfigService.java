package com.photoexhibition.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SmsConfigService {

    public static final String SMS_PROVIDER_TYPE_KEY = "sms_provider_type";
    public static final String SMS_PROVIDER_TYPE_DESCRIPTION = "短信平台类型（ALIYUN / TENCENT_CLOUD / TWILIO / HUAWEI_CLOUD / VOLCENGINE / CLOOPEN / AWS_SNS / YUNPIAN / SUBMAIL / MESSAGEBIRD / VONAGE / INFOBIP / PLIVO / SINCH / TELNYX / SMSAERO / HTTP_WEBHOOK）";

    public static final String SMS_ENABLED_KEY = "sms_enabled";
    public static final String SMS_ENABLED_DESCRIPTION = "是否启用真实短信发送";

    public static final String SMS_MOCK_ENABLED_KEY = "sms_mock_enabled";
    public static final String SMS_MOCK_ENABLED_DESCRIPTION = "短信未启用时是否允许 mock 模式返回验证码";

    public static final String SMS_ENDPOINT_KEY = "sms_endpoint";
    public static final String SMS_ENDPOINT_DESCRIPTION = "短信平台 API Endpoint / Webhook 地址";

    public static final String SMS_REGION_ID_KEY = "sms_region_id";
    public static final String SMS_REGION_ID_DESCRIPTION = "短信平台 RegionId / 子账号区 / 额外路由参数";

    public static final String SMS_ACCESS_KEY_ID_KEY = "sms_access_key_id";
    public static final String SMS_ACCESS_KEY_ID_DESCRIPTION = "短信平台 AccessKeyId / SecretId / Account SID";

    public static final String SMS_ACCESS_KEY_SECRET_KEY = "sms_access_key_secret";
    public static final String SMS_ACCESS_KEY_SECRET_DESCRIPTION = "短信平台 AccessKeySecret / SecretKey / Auth Token";

    public static final String SMS_SIGN_NAME_KEY = "sms_sign_name";
    public static final String SMS_SIGN_NAME_DESCRIPTION = "短信签名 / 发送号码 / Service SID";

    public static final String SMS_TEMPLATE_CODE_KEY = "sms_template_code";
    public static final String SMS_TEMPLATE_CODE_DESCRIPTION = "短信模板编号 / TemplateId / MessagingServiceSid";

    public static final String SMS_TEMPLATE_PARAM_NAME_KEY = "sms_template_param_name";
    public static final String SMS_TEMPLATE_PARAM_NAME_DESCRIPTION = "短信模板参数名";

    public static final String SMS_SDK_APP_ID_KEY = "sms_sdk_app_id";
    public static final String SMS_SDK_APP_ID_DESCRIPTION = "腾讯云短信 SmsSdkAppId";

    public static final String SMS_CODE_EXPIRE_MINUTES_KEY = "sms_code_expire_minutes";
    public static final String SMS_CODE_EXPIRE_MINUTES_DESCRIPTION = "短信验证码有效期（分钟）";

    private final SystemConfigService systemConfigService;
    private final SmsProperties smsProperties;

    public SmsResolvedSettings getResolvedSettings() {
        return SmsResolvedSettings.builder()
            .providerType(getProviderType())
            .enabled(getBoolean(SMS_ENABLED_KEY, smsProperties.isEnabled(), SMS_ENABLED_DESCRIPTION))
            .mockEnabled(getBoolean(SMS_MOCK_ENABLED_KEY, smsProperties.isMockEnabled(), SMS_MOCK_ENABLED_DESCRIPTION))
            .endpoint(getString(SMS_ENDPOINT_KEY, smsProperties.getEndpoint(), SMS_ENDPOINT_DESCRIPTION))
            .regionId(getString(SMS_REGION_ID_KEY, smsProperties.getRegionId(), SMS_REGION_ID_DESCRIPTION))
            .accessKeyId(getString(SMS_ACCESS_KEY_ID_KEY, smsProperties.getAccessKeyId(), SMS_ACCESS_KEY_ID_DESCRIPTION))
            .accessKeySecret(getString(SMS_ACCESS_KEY_SECRET_KEY, smsProperties.getAccessKeySecret(), SMS_ACCESS_KEY_SECRET_DESCRIPTION))
            .signName(getString(SMS_SIGN_NAME_KEY, smsProperties.getSignName(), SMS_SIGN_NAME_DESCRIPTION))
            .templateCode(getString(SMS_TEMPLATE_CODE_KEY, smsProperties.getTemplateCode(), SMS_TEMPLATE_CODE_DESCRIPTION))
            .templateParamName(getString(SMS_TEMPLATE_PARAM_NAME_KEY, smsProperties.getTemplateParamName(), SMS_TEMPLATE_PARAM_NAME_DESCRIPTION))
            .sdkAppId(getString(SMS_SDK_APP_ID_KEY, smsProperties.getSdkAppId(), SMS_SDK_APP_ID_DESCRIPTION))
            .codeExpireMinutes(getInt(SMS_CODE_EXPIRE_MINUTES_KEY, smsProperties.getCodeExpireMinutes(), SMS_CODE_EXPIRE_MINUTES_DESCRIPTION, 1))
            .build();
    }

    @Transactional
    public void setProviderType(String providerType) {
        SmsProviderType type;
        try {
            type = SmsProviderType.valueOf((providerType == null ? "" : providerType.trim()).toUpperCase());
        } catch (Exception e) {
            throw new RuntimeException("smsProviderType 不合法");
        }
        systemConfigService.setConfigValue(SMS_PROVIDER_TYPE_KEY, type.name(), SMS_PROVIDER_TYPE_DESCRIPTION);
    }

    @Transactional
    public void setEnabled(boolean enabled) {
        systemConfigService.setConfigValue(SMS_ENABLED_KEY, String.valueOf(enabled), SMS_ENABLED_DESCRIPTION);
    }

    @Transactional
    public void setMockEnabled(boolean enabled) {
        systemConfigService.setConfigValue(SMS_MOCK_ENABLED_KEY, String.valueOf(enabled), SMS_MOCK_ENABLED_DESCRIPTION);
    }

    @Transactional
    public void setEndpoint(String endpoint) {
        systemConfigService.setConfigValue(SMS_ENDPOINT_KEY, safeTrim(endpoint), SMS_ENDPOINT_DESCRIPTION);
    }

    @Transactional
    public void setRegionId(String regionId) {
        systemConfigService.setConfigValue(SMS_REGION_ID_KEY, safeTrim(regionId), SMS_REGION_ID_DESCRIPTION);
    }

    @Transactional
    public void setAccessKeyId(String accessKeyId) {
        systemConfigService.setConfigValue(SMS_ACCESS_KEY_ID_KEY, safeTrim(accessKeyId), SMS_ACCESS_KEY_ID_DESCRIPTION);
    }

    @Transactional
    public void setAccessKeySecret(String accessKeySecret) {
        systemConfigService.setConfigValue(SMS_ACCESS_KEY_SECRET_KEY, safeTrim(accessKeySecret), SMS_ACCESS_KEY_SECRET_DESCRIPTION);
    }

    @Transactional
    public void setSignName(String signName) {
        systemConfigService.setConfigValue(SMS_SIGN_NAME_KEY, safeTrim(signName), SMS_SIGN_NAME_DESCRIPTION);
    }

    @Transactional
    public void setTemplateCode(String templateCode) {
        systemConfigService.setConfigValue(SMS_TEMPLATE_CODE_KEY, safeTrim(templateCode), SMS_TEMPLATE_CODE_DESCRIPTION);
    }

    @Transactional
    public void setTemplateParamName(String templateParamName) {
        String next = safeTrim(templateParamName);
        if (next == null || next.isBlank()) {
            next = smsProperties.getTemplateParamName();
        }
        systemConfigService.setConfigValue(SMS_TEMPLATE_PARAM_NAME_KEY, next, SMS_TEMPLATE_PARAM_NAME_DESCRIPTION);
    }

    @Transactional
    public void setSdkAppId(String sdkAppId) {
        systemConfigService.setConfigValue(SMS_SDK_APP_ID_KEY, safeTrim(sdkAppId), SMS_SDK_APP_ID_DESCRIPTION);
    }

    @Transactional
    public void setCodeExpireMinutes(int minutes) {
        if (minutes < 1) {
            throw new RuntimeException("smsCodeExpireMinutes 不能小于 1");
        }
        systemConfigService.setConfigValue(SMS_CODE_EXPIRE_MINUTES_KEY, String.valueOf(minutes), SMS_CODE_EXPIRE_MINUTES_DESCRIPTION);
    }

    private boolean getBoolean(String key, boolean fallback, String description) {
        return Boolean.parseBoolean(systemConfigService.getConfigValueWithDefault(key, String.valueOf(fallback), description));
    }

    private SmsProviderType getProviderType() {
        String raw = systemConfigService.getConfigValueWithDefault(
            SMS_PROVIDER_TYPE_KEY,
            SmsProviderType.ALIYUN.name(),
            SMS_PROVIDER_TYPE_DESCRIPTION
        );
        try {
            return SmsProviderType.valueOf(raw);
        } catch (Exception e) {
            return SmsProviderType.ALIYUN;
        }
    }

    private String getString(String key, String fallback, String description) {
        return systemConfigService.getConfigValueWithDefault(key, fallback == null ? "" : fallback, description);
    }

    private int getInt(String key, int fallback, String description, int minValue) {
        try {
            return Math.max(minValue, Integer.parseInt(systemConfigService.getConfigValueWithDefault(key, String.valueOf(fallback), description)));
        } catch (NumberFormatException e) {
            return Math.max(minValue, fallback);
        }
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    @Data
    @Builder
    public static class SmsResolvedSettings {
        private SmsProviderType providerType;
        private boolean enabled;
        private boolean mockEnabled;
        private String endpoint;
        private String regionId;
        private String accessKeyId;
        private String accessKeySecret;
        private String signName;
        private String templateCode;
        private String templateParamName;
        private String sdkAppId;
        private int codeExpireMinutes;
    }
}
