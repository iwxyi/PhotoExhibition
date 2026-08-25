package com.photoexhibition.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailConfigService {

    public static final String EMAIL_PROVIDER_TYPE_KEY = "email_provider_type";
    public static final String EMAIL_PROVIDER_TYPE_DESCRIPTION = "邮件发送平台类型（当前以 SMTP 兼容方式接入常见邮件平台）";

    public static final String EMAIL_ENABLED_KEY = "email_enabled";
    public static final String EMAIL_ENABLED_DESCRIPTION = "是否启用邮件发送";

    public static final String EMAIL_HOST_KEY = "email_host";
    public static final String EMAIL_HOST_DESCRIPTION = "SMTP 主机地址";

    public static final String EMAIL_PORT_KEY = "email_port";
    public static final String EMAIL_PORT_DESCRIPTION = "SMTP 端口";

    public static final String EMAIL_USERNAME_KEY = "email_username";
    public static final String EMAIL_USERNAME_DESCRIPTION = "SMTP 用户名";

    public static final String EMAIL_PASSWORD_KEY = "email_password";
    public static final String EMAIL_PASSWORD_DESCRIPTION = "SMTP 密码 / 授权码";

    public static final String EMAIL_PROTOCOL_KEY = "email_protocol";
    public static final String EMAIL_PROTOCOL_DESCRIPTION = "邮件协议（smtp / smtps）";

    public static final String EMAIL_FROM_ADDRESS_KEY = "email_from_address";
    public static final String EMAIL_FROM_ADDRESS_DESCRIPTION = "发件邮箱地址";

    public static final String EMAIL_FROM_NAME_KEY = "email_from_name";
    public static final String EMAIL_FROM_NAME_DESCRIPTION = "发件人显示名称";

    public static final String EMAIL_REPLY_TO_KEY = "email_reply_to";
    public static final String EMAIL_REPLY_TO_DESCRIPTION = "回复邮箱";

    public static final String EMAIL_SSL_ENABLED_KEY = "email_ssl_enabled";
    public static final String EMAIL_SSL_ENABLED_DESCRIPTION = "是否启用 SSL";

    public static final String EMAIL_STARTTLS_ENABLED_KEY = "email_starttls_enabled";
    public static final String EMAIL_STARTTLS_ENABLED_DESCRIPTION = "是否启用 STARTTLS";

    public static final String EMAIL_TEST_RECIPIENT_KEY = "email_test_recipient";
    public static final String EMAIL_TEST_RECIPIENT_DESCRIPTION = "测试邮件默认收件人";

    public static final String EMAIL_MOCK_ENABLED_KEY = "email_mock_enabled";
    public static final String EMAIL_MOCK_ENABLED_DESCRIPTION = "是否启用邮箱验证码 Mock 模式";

    public static final String EMAIL_CODE_LOGIN_ENABLED_KEY = "email_code_login_enabled";
    public static final String EMAIL_CODE_LOGIN_ENABLED_DESCRIPTION = "是否启用邮箱验证码登录";

    public static final String EMAIL_CODE_EXPIRE_MINUTES_KEY = "email_code_expire_minutes";
    public static final String EMAIL_CODE_EXPIRE_MINUTES_DESCRIPTION = "邮箱验证码有效期（分钟）";

    private final SystemConfigService systemConfigService;

    public EmailResolvedSettings getResolvedSettings() {
        return EmailResolvedSettings.builder()
            .providerType(getProviderType())
            .enabled(getBoolean(EMAIL_ENABLED_KEY, false, EMAIL_ENABLED_DESCRIPTION))
            .host(getString(EMAIL_HOST_KEY, "", EMAIL_HOST_DESCRIPTION))
            .port(getInt(EMAIL_PORT_KEY, 465, EMAIL_PORT_DESCRIPTION, 1))
            .username(getString(EMAIL_USERNAME_KEY, "", EMAIL_USERNAME_DESCRIPTION))
            .password(getString(EMAIL_PASSWORD_KEY, "", EMAIL_PASSWORD_DESCRIPTION))
            .protocol(getString(EMAIL_PROTOCOL_KEY, "smtp", EMAIL_PROTOCOL_DESCRIPTION))
            .fromAddress(getString(EMAIL_FROM_ADDRESS_KEY, "", EMAIL_FROM_ADDRESS_DESCRIPTION))
            .fromName(getString(EMAIL_FROM_NAME_KEY, "", EMAIL_FROM_NAME_DESCRIPTION))
            .replyTo(getString(EMAIL_REPLY_TO_KEY, "", EMAIL_REPLY_TO_DESCRIPTION))
            .sslEnabled(getBoolean(EMAIL_SSL_ENABLED_KEY, true, EMAIL_SSL_ENABLED_DESCRIPTION))
            .starttlsEnabled(getBoolean(EMAIL_STARTTLS_ENABLED_KEY, true, EMAIL_STARTTLS_ENABLED_DESCRIPTION))
            .testRecipient(getString(EMAIL_TEST_RECIPIENT_KEY, "", EMAIL_TEST_RECIPIENT_DESCRIPTION))
            .mockEnabled(getBoolean(EMAIL_MOCK_ENABLED_KEY, false, EMAIL_MOCK_ENABLED_DESCRIPTION))
            .codeLoginEnabled(getBoolean(EMAIL_CODE_LOGIN_ENABLED_KEY, false, EMAIL_CODE_LOGIN_ENABLED_DESCRIPTION))
            .codeExpireMinutes(getInt(EMAIL_CODE_EXPIRE_MINUTES_KEY, 5, EMAIL_CODE_EXPIRE_MINUTES_DESCRIPTION, 1))
            .build();
    }

    @Transactional
    public void setProviderType(String providerType) {
        EmailProviderType type;
        try {
            type = EmailProviderType.valueOf((providerType == null ? "" : providerType.trim()).toUpperCase());
        } catch (Exception e) {
            throw new RuntimeException("emailProviderType 不合法");
        }
        systemConfigService.setConfigValue(EMAIL_PROVIDER_TYPE_KEY, type.name(), EMAIL_PROVIDER_TYPE_DESCRIPTION);
    }

    @Transactional public void setEnabled(boolean enabled) { systemConfigService.setConfigValue(EMAIL_ENABLED_KEY, String.valueOf(enabled), EMAIL_ENABLED_DESCRIPTION); }
    @Transactional public void setHost(String host) { systemConfigService.setConfigValue(EMAIL_HOST_KEY, safeTrim(host), EMAIL_HOST_DESCRIPTION); }
    @Transactional public void setPort(int port) {
        if (port < 1) throw new RuntimeException("emailPort 不能小于 1");
        systemConfigService.setConfigValue(EMAIL_PORT_KEY, String.valueOf(port), EMAIL_PORT_DESCRIPTION);
    }
    @Transactional public void setUsername(String username) { systemConfigService.setConfigValue(EMAIL_USERNAME_KEY, safeTrim(username), EMAIL_USERNAME_DESCRIPTION); }
    @Transactional public void setPassword(String password) { systemConfigService.setConfigValue(EMAIL_PASSWORD_KEY, safeTrim(password), EMAIL_PASSWORD_DESCRIPTION); }
    @Transactional public void setProtocol(String protocol) { systemConfigService.setConfigValue(EMAIL_PROTOCOL_KEY, safeTrim(protocol), EMAIL_PROTOCOL_DESCRIPTION); }
    @Transactional public void setFromAddress(String fromAddress) { systemConfigService.setConfigValue(EMAIL_FROM_ADDRESS_KEY, safeTrim(fromAddress), EMAIL_FROM_ADDRESS_DESCRIPTION); }
    @Transactional public void setFromName(String fromName) { systemConfigService.setConfigValue(EMAIL_FROM_NAME_KEY, safeTrim(fromName), EMAIL_FROM_NAME_DESCRIPTION); }
    @Transactional public void setReplyTo(String replyTo) { systemConfigService.setConfigValue(EMAIL_REPLY_TO_KEY, safeTrim(replyTo), EMAIL_REPLY_TO_DESCRIPTION); }
    @Transactional public void setSslEnabled(boolean enabled) { systemConfigService.setConfigValue(EMAIL_SSL_ENABLED_KEY, String.valueOf(enabled), EMAIL_SSL_ENABLED_DESCRIPTION); }
    @Transactional public void setStarttlsEnabled(boolean enabled) { systemConfigService.setConfigValue(EMAIL_STARTTLS_ENABLED_KEY, String.valueOf(enabled), EMAIL_STARTTLS_ENABLED_DESCRIPTION); }
    @Transactional public void setTestRecipient(String recipient) { systemConfigService.setConfigValue(EMAIL_TEST_RECIPIENT_KEY, safeTrim(recipient), EMAIL_TEST_RECIPIENT_DESCRIPTION); }
    @Transactional public void setMockEnabled(boolean enabled) { systemConfigService.setConfigValue(EMAIL_MOCK_ENABLED_KEY, String.valueOf(enabled), EMAIL_MOCK_ENABLED_DESCRIPTION); }
    @Transactional public void setCodeLoginEnabled(boolean enabled) { systemConfigService.setConfigValue(EMAIL_CODE_LOGIN_ENABLED_KEY, String.valueOf(enabled), EMAIL_CODE_LOGIN_ENABLED_DESCRIPTION); }
    @Transactional public void setCodeExpireMinutes(int minutes) {
        if (minutes < 1) throw new RuntimeException("emailCodeExpireMinutes 不能小于 1");
        systemConfigService.setConfigValue(EMAIL_CODE_EXPIRE_MINUTES_KEY, String.valueOf(minutes), EMAIL_CODE_EXPIRE_MINUTES_DESCRIPTION);
    }

    private boolean getBoolean(String key, boolean fallback, String description) {
        return Boolean.parseBoolean(systemConfigService.getConfigValueWithDefault(key, String.valueOf(fallback), description));
    }

    private int getInt(String key, int fallback, String description, int minValue) {
        try {
            return Math.max(minValue, Integer.parseInt(systemConfigService.getConfigValueWithDefault(key, String.valueOf(fallback), description)));
        } catch (NumberFormatException e) {
            return Math.max(minValue, fallback);
        }
    }

    private String getString(String key, String fallback, String description) {
        return systemConfigService.getConfigValueWithDefault(key, fallback == null ? "" : fallback, description);
    }

    private EmailProviderType getProviderType() {
        String raw = systemConfigService.getConfigValueWithDefault(
            EMAIL_PROVIDER_TYPE_KEY,
            EmailProviderType.SMTP.name(),
            EMAIL_PROVIDER_TYPE_DESCRIPTION
        );
        try {
            return EmailProviderType.valueOf(raw);
        } catch (Exception e) {
            return EmailProviderType.SMTP;
        }
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    @Data
    @Builder
    public static class EmailResolvedSettings {
        private EmailProviderType providerType;
        private boolean enabled;
        private String host;
        private int port;
        private String username;
        private String password;
        private String protocol;
        private String fromAddress;
        private String fromName;
        private String replyTo;
        private boolean sslEnabled;
        private boolean starttlsEnabled;
        private String testRecipient;
        private boolean mockEnabled;
        private boolean codeLoginEnabled;
        private int codeExpireMinutes;
    }
}
