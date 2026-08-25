package com.photoexhibition.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentConfigService {

    public static final String PAYMENT_PROVIDER_TYPE_KEY = "payment_provider_type";
    public static final String PAYMENT_PROVIDER_TYPE_DESCRIPTION = "支付平台类型";
    public static final String PAYMENT_ENABLED_KEY = "payment_enabled";
    public static final String PAYMENT_ENABLED_DESCRIPTION = "是否启用支付";
    public static final String PAYMENT_MOCK_ENABLED_KEY = "payment_mock_enabled";
    public static final String PAYMENT_MOCK_ENABLED_DESCRIPTION = "是否启用支付模拟模式";
    public static final String PAYMENT_APP_ID_KEY = "payment_app_id";
    public static final String PAYMENT_APP_ID_DESCRIPTION = "支付应用ID";
    public static final String PAYMENT_MERCHANT_ID_KEY = "payment_merchant_id";
    public static final String PAYMENT_MERCHANT_ID_DESCRIPTION = "支付商户号";
    public static final String PAYMENT_MERCHANT_NAME_KEY = "payment_merchant_name";
    public static final String PAYMENT_MERCHANT_NAME_DESCRIPTION = "支付商户名称";
    public static final String PAYMENT_PRIVATE_KEY_KEY = "payment_private_key";
    public static final String PAYMENT_PRIVATE_KEY_DESCRIPTION = "支付私钥";
    public static final String PAYMENT_PUBLIC_KEY_KEY = "payment_public_key";
    public static final String PAYMENT_PUBLIC_KEY_DESCRIPTION = "支付公钥/平台公钥";
    public static final String PAYMENT_API_BASE_URL_KEY = "payment_api_base_url";
    public static final String PAYMENT_API_BASE_URL_DESCRIPTION = "支付API基础地址";
    public static final String PAYMENT_NOTIFY_URL_KEY = "payment_notify_url";
    public static final String PAYMENT_NOTIFY_URL_DESCRIPTION = "支付回调地址";
    public static final String PAYMENT_RETURN_URL_KEY = "payment_return_url";
    public static final String PAYMENT_RETURN_URL_DESCRIPTION = "支付完成返回地址";
    public static final String PAYMENT_WEBHOOK_SECRET_KEY = "payment_webhook_secret";
    public static final String PAYMENT_WEBHOOK_SECRET_DESCRIPTION = "支付Webhook密钥";
    public static final String PAYMENT_CURRENCY_KEY = "payment_currency";
    public static final String PAYMENT_CURRENCY_DESCRIPTION = "支付币种";
    public static final String PAYMENT_VERIFICATION_MODE_KEY = "payment_verification_mode";
    public static final String PAYMENT_VERIFICATION_MODE_DESCRIPTION = "支付回调验签模式";
    public static final String PAYMENT_API_SECRET_KEY = "payment_api_secret";
    public static final String PAYMENT_API_SECRET_DESCRIPTION = "支付API Secret/APIv3 Key";
    public static final String PAYMENT_CERTIFICATE_SERIAL_NO_KEY = "payment_certificate_serial_no";
    public static final String PAYMENT_CERTIFICATE_SERIAL_NO_DESCRIPTION = "支付证书序列号";
    public static final String PAYMENT_PLATFORM_CERTIFICATE_KEY = "payment_platform_certificate";
    public static final String PAYMENT_PLATFORM_CERTIFICATE_DESCRIPTION = "支付平台证书 / 公钥链";

    private final SystemConfigService systemConfigService;

    public PaymentResolvedSettings getResolvedSettings() {
        PaymentProviderType providerType = getProviderType();
        return PaymentResolvedSettings.builder()
            .providerType(providerType)
            .enabled(getBoolean(PAYMENT_ENABLED_KEY, false, PAYMENT_ENABLED_DESCRIPTION))
            .mockEnabled(getBoolean(PAYMENT_MOCK_ENABLED_KEY, true, PAYMENT_MOCK_ENABLED_DESCRIPTION))
            .appId(getString(PAYMENT_APP_ID_KEY, "", PAYMENT_APP_ID_DESCRIPTION))
            .merchantId(getString(PAYMENT_MERCHANT_ID_KEY, "", PAYMENT_MERCHANT_ID_DESCRIPTION))
            .merchantName(getString(PAYMENT_MERCHANT_NAME_KEY, "", PAYMENT_MERCHANT_NAME_DESCRIPTION))
            .privateKey(getString(PAYMENT_PRIVATE_KEY_KEY, "", PAYMENT_PRIVATE_KEY_DESCRIPTION))
            .publicKey(getString(PAYMENT_PUBLIC_KEY_KEY, "", PAYMENT_PUBLIC_KEY_DESCRIPTION))
            .apiBaseUrl(getString(PAYMENT_API_BASE_URL_KEY, "", PAYMENT_API_BASE_URL_DESCRIPTION))
            .notifyUrl(getString(PAYMENT_NOTIFY_URL_KEY, "", PAYMENT_NOTIFY_URL_DESCRIPTION))
            .returnUrl(getString(PAYMENT_RETURN_URL_KEY, "", PAYMENT_RETURN_URL_DESCRIPTION))
            .webhookSecret(getString(PAYMENT_WEBHOOK_SECRET_KEY, "", PAYMENT_WEBHOOK_SECRET_DESCRIPTION))
            .currency(getString(PAYMENT_CURRENCY_KEY, "CNY", PAYMENT_CURRENCY_DESCRIPTION))
            .verificationMode(normalizeVerificationMode(
                providerType,
                getString(PAYMENT_VERIFICATION_MODE_KEY, "AUTO", PAYMENT_VERIFICATION_MODE_DESCRIPTION)
            ))
            .apiSecret(getString(PAYMENT_API_SECRET_KEY, "", PAYMENT_API_SECRET_DESCRIPTION))
            .certificateSerialNo(getString(PAYMENT_CERTIFICATE_SERIAL_NO_KEY, "", PAYMENT_CERTIFICATE_SERIAL_NO_DESCRIPTION))
            .platformCertificate(getString(PAYMENT_PLATFORM_CERTIFICATE_KEY, "", PAYMENT_PLATFORM_CERTIFICATE_DESCRIPTION))
            .build();
    }

    @Transactional
    public void setProviderType(String providerType) {
        PaymentProviderType type;
        try {
            type = PaymentProviderType.valueOf((providerType == null ? "" : providerType.trim()).toUpperCase());
        } catch (Exception e) {
            throw new RuntimeException("paymentProviderType 不合法");
        }
        systemConfigService.setConfigValue(PAYMENT_PROVIDER_TYPE_KEY, type.name(), PAYMENT_PROVIDER_TYPE_DESCRIPTION);
        String currentMode = getString(PAYMENT_VERIFICATION_MODE_KEY, "AUTO", PAYMENT_VERIFICATION_MODE_DESCRIPTION);
        systemConfigService.setConfigValue(
            PAYMENT_VERIFICATION_MODE_KEY,
            normalizeVerificationMode(type, currentMode),
            PAYMENT_VERIFICATION_MODE_DESCRIPTION
        );
    }

    @Transactional public void setEnabled(boolean enabled) { systemConfigService.setConfigValue(PAYMENT_ENABLED_KEY, String.valueOf(enabled), PAYMENT_ENABLED_DESCRIPTION); }
    @Transactional public void setMockEnabled(boolean enabled) { systemConfigService.setConfigValue(PAYMENT_MOCK_ENABLED_KEY, String.valueOf(enabled), PAYMENT_MOCK_ENABLED_DESCRIPTION); }
    @Transactional public void setAppId(String value) { systemConfigService.setConfigValue(PAYMENT_APP_ID_KEY, safeTrim(value), PAYMENT_APP_ID_DESCRIPTION); }
    @Transactional public void setMerchantId(String value) { systemConfigService.setConfigValue(PAYMENT_MERCHANT_ID_KEY, safeTrim(value), PAYMENT_MERCHANT_ID_DESCRIPTION); }
    @Transactional public void setMerchantName(String value) { systemConfigService.setConfigValue(PAYMENT_MERCHANT_NAME_KEY, safeTrim(value), PAYMENT_MERCHANT_NAME_DESCRIPTION); }
    @Transactional public void setPrivateKey(String value) { systemConfigService.setConfigValue(PAYMENT_PRIVATE_KEY_KEY, safeTrim(value), PAYMENT_PRIVATE_KEY_DESCRIPTION); }
    @Transactional public void setPublicKey(String value) { systemConfigService.setConfigValue(PAYMENT_PUBLIC_KEY_KEY, safeTrim(value), PAYMENT_PUBLIC_KEY_DESCRIPTION); }
    @Transactional public void setApiBaseUrl(String value) { systemConfigService.setConfigValue(PAYMENT_API_BASE_URL_KEY, safeTrim(value), PAYMENT_API_BASE_URL_DESCRIPTION); }
    @Transactional public void setNotifyUrl(String value) { systemConfigService.setConfigValue(PAYMENT_NOTIFY_URL_KEY, safeTrim(value), PAYMENT_NOTIFY_URL_DESCRIPTION); }
    @Transactional public void setReturnUrl(String value) { systemConfigService.setConfigValue(PAYMENT_RETURN_URL_KEY, safeTrim(value), PAYMENT_RETURN_URL_DESCRIPTION); }
    @Transactional public void setWebhookSecret(String value) { systemConfigService.setConfigValue(PAYMENT_WEBHOOK_SECRET_KEY, safeTrim(value), PAYMENT_WEBHOOK_SECRET_DESCRIPTION); }
    @Transactional public void setCurrency(String value) { systemConfigService.setConfigValue(PAYMENT_CURRENCY_KEY, safeTrim(value), PAYMENT_CURRENCY_DESCRIPTION); }
    @Transactional public void setVerificationMode(String value) {
        systemConfigService.setConfigValue(
            PAYMENT_VERIFICATION_MODE_KEY,
            normalizeVerificationMode(getProviderType(), value),
            PAYMENT_VERIFICATION_MODE_DESCRIPTION
        );
    }
    @Transactional public void setApiSecret(String value) { systemConfigService.setConfigValue(PAYMENT_API_SECRET_KEY, safeTrim(value), PAYMENT_API_SECRET_DESCRIPTION); }
    @Transactional public void setCertificateSerialNo(String value) { systemConfigService.setConfigValue(PAYMENT_CERTIFICATE_SERIAL_NO_KEY, safeTrim(value), PAYMENT_CERTIFICATE_SERIAL_NO_DESCRIPTION); }
    @Transactional public void setPlatformCertificate(String value) { systemConfigService.setConfigValue(PAYMENT_PLATFORM_CERTIFICATE_KEY, safeTrim(value), PAYMENT_PLATFORM_CERTIFICATE_DESCRIPTION); }

    private boolean getBoolean(String key, boolean fallback, String description) {
        return Boolean.parseBoolean(systemConfigService.getConfigValueWithDefault(key, String.valueOf(fallback), description));
    }

    private String getString(String key, String fallback, String description) {
        return systemConfigService.getConfigValueWithDefault(key, fallback, description);
    }

    private PaymentProviderType getProviderType() {
        String raw = systemConfigService.getConfigValueWithDefault(PAYMENT_PROVIDER_TYPE_KEY, PaymentProviderType.ALIPAY.name(), PAYMENT_PROVIDER_TYPE_DESCRIPTION);
        try {
            return PaymentProviderType.valueOf(raw);
        } catch (Exception e) {
            return PaymentProviderType.ALIPAY;
        }
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeVerificationMode(PaymentProviderType providerType, String value) {
        String normalized = safeTrim(value).toUpperCase();
        if (normalized.isEmpty()) {
            normalized = "AUTO";
        }
        if (isAllowedVerificationMode(providerType, normalized)) {
            return normalized;
        }
        return "AUTO";
    }

    private boolean isAllowedVerificationMode(PaymentProviderType providerType, String mode) {
        switch (providerType) {
            case ALIPAY:
                return "AUTO".equals(mode) || "RSA".equals(mode);
            case WECHAT_PAY:
                return "AUTO".equals(mode) || "HMAC".equals(mode) || "CERTIFICATE".equals(mode);
            case STRIPE:
            case PADDLE:
            case LEMON_SQUEEZY:
            case ADYEN:
            case XENDIT:
                return "AUTO".equals(mode) || "HMAC".equals(mode);
            case PAYPAL:
                return "AUTO".equals(mode) || "HMAC".equals(mode) || "RSA".equals(mode);
            case UNIONPAY:
                return "AUTO".equals(mode) || "RSA".equals(mode) || "CERTIFICATE".equals(mode);
            case MOLLIE:
                return "AUTO".equals(mode) || "CUSTOM".equals(mode);
            case MIDTRANS:
                return "AUTO".equals(mode) || "HMAC".equals(mode) || "CUSTOM".equals(mode);
            case CUSTOM_WEBHOOK:
            default:
                return "AUTO".equals(mode)
                    || "HMAC".equals(mode)
                    || "RSA".equals(mode)
                    || "CERTIFICATE".equals(mode)
                    || "CUSTOM".equals(mode);
        }
    }

    @Data
    @Builder
    public static class PaymentResolvedSettings {
        private PaymentProviderType providerType;
        private boolean enabled;
        private boolean mockEnabled;
        private String appId;
        private String merchantId;
        private String merchantName;
        private String privateKey;
        private String publicKey;
        private String apiBaseUrl;
        private String notifyUrl;
        private String returnUrl;
        private String webhookSecret;
        private String currency;
        private String verificationMode;
        private String apiSecret;
        private String certificateSerialNo;
        private String platformCertificate;
    }
}
