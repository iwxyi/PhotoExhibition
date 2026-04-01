package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.entity.VipPlan;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentGatewayService {

    private static final DateTimeFormatter ORDER_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PaymentConfigService paymentConfigService;

    public PaymentPreview preview(UserPlanOrder order, VipPlan plan, UserAccount user) {
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (plan == null) {
            throw new RuntimeException("VIP 套餐不存在");
        }
        if (user == null) {
            throw new RuntimeException("订单用户不存在");
        }

        PaymentConfigService.PaymentResolvedSettings settings = paymentConfigService.getResolvedSettings();
        PaymentProviderType providerType = settings.getProviderType() == null
            ? PaymentProviderType.ALIPAY
            : settings.getProviderType();
        List<String> missingFields = resolveMissingFields(providerType, settings);
        boolean mockMode = settings.isMockEnabled();
        boolean enabled = settings.isEnabled();
        List<String> readinessWarnings = resolveReadinessWarnings(providerType, settings);
        boolean liveModeReady = enabled && missingFields.isEmpty() && readinessWarnings.isEmpty();

        Map<String, Object> requestPayload = buildRequestPayload(providerType, settings, order, plan, user);
        Map<String, Object> callbackPayload = buildCallbackPayload(providerType, settings, order, user);

        return PaymentPreview.builder()
            .providerType(providerType.name())
            .providerLabel(resolveProviderLabel(providerType))
            .enabled(enabled)
            .mockEnabled(mockMode)
            .liveModeReady(liveModeReady)
            .currency(safeString(settings.getCurrency(), "CNY"))
            .apiBaseUrl(resolveApiBaseUrl(providerType, settings))
            .missingFields(missingFields)
            .readinessWarnings(readinessWarnings)
            .signatureReady(isInitiationSignatureReady(providerType, settings))
            .callbackVerificationReady(isCallbackVerificationReady(providerType, settings))
            .refundReady(isRefundReady(providerType, settings))
            .stageReadiness(resolveStageReadiness(providerType, settings))
            .recommendedConfigFields(resolveRecommendedConfigFields(providerType))
            .nextActionHints(resolveNextActionHints(providerType, settings))
            .supportMessage(buildSupportMessage(enabled, mockMode, liveModeReady, missingFields, readinessWarnings))
            .verificationMode(resolveSuggestedVerificationMode(providerType, settings))
            .initiationMode(resolveInitiationMode(providerType))
            .refundMode(resolveRefundMode(providerType))
            .capabilityTags(resolveCapabilityTags(providerType))
            .integrationSteps(resolveIntegrationSteps(providerType))
            .requestPayload(requestPayload)
            .callbackPayload(callbackPayload)
            .build();
    }

    private List<String> resolveRecommendedConfigFields(PaymentProviderType providerType) {
        List<String> fields = new ArrayList<>();
        switch (providerType) {
            case ALIPAY:
                fields.add("paymentAppId");
                fields.add("paymentMerchantId");
                fields.add("paymentPrivateKey");
                fields.add("paymentPublicKey");
                fields.add("paymentNotifyUrl");
                fields.add("paymentReturnUrl");
                break;
            case WECHAT_PAY:
                fields.add("paymentAppId");
                fields.add("paymentMerchantId");
                fields.add("paymentPrivateKey");
                fields.add("paymentApiSecret");
                fields.add("paymentCertificateSerialNo");
                fields.add("paymentPlatformCertificate");
                fields.add("paymentNotifyUrl");
                break;
            case STRIPE:
                fields.add("paymentPrivateKey");
                fields.add("paymentWebhookSecret");
                fields.add("paymentNotifyUrl");
                fields.add("paymentReturnUrl");
                break;
            case PAYPAL:
                fields.add("paymentAppId");
                fields.add("paymentPrivateKey");
                fields.add("paymentMerchantId");
                fields.add("paymentWebhookSecret");
                fields.add("paymentNotifyUrl");
                fields.add("paymentReturnUrl");
                break;
            case UNIONPAY:
                fields.add("paymentMerchantId");
                fields.add("paymentPrivateKey");
                fields.add("paymentPublicKey");
                fields.add("paymentNotifyUrl");
                fields.add("paymentReturnUrl");
                break;
            case PADDLE:
            case LEMON_SQUEEZY:
                fields.add("paymentAppId");
                fields.add("paymentPrivateKey");
                fields.add("paymentWebhookSecret");
                fields.add("paymentNotifyUrl");
                fields.add("paymentReturnUrl");
                break;
            case ADYEN:
            case XENDIT:
                fields.add("paymentMerchantId");
                fields.add("paymentApiSecret");
                fields.add("paymentWebhookSecret");
                fields.add("paymentNotifyUrl");
                fields.add("paymentReturnUrl");
                break;
            case MOLLIE:
                fields.add("paymentPrivateKey");
                fields.add("paymentNotifyUrl");
                fields.add("paymentReturnUrl");
                break;
            case MIDTRANS:
                fields.add("paymentMerchantId");
                fields.add("paymentPrivateKey");
                fields.add("paymentNotifyUrl");
                fields.add("paymentReturnUrl");
                break;
            case CUSTOM_WEBHOOK:
            default:
                fields.add("paymentApiBaseUrl");
                fields.add("paymentPrivateKey");
                fields.add("paymentNotifyUrl");
                break;
        }
        return fields;
    }

    private List<String> resolveNextActionHints(PaymentProviderType providerType,
                                                PaymentConfigService.PaymentResolvedSettings settings) {
        List<String> hints = new ArrayList<>();
        if (!isInitiationSignatureReady(providerType, settings)) {
            hints.add("先补齐下单阶段所需的签名材料或 API 凭证。");
        }
        if (!isCallbackVerificationReady(providerType, settings)) {
            hints.add("补齐回调验签材料，并用支付预览回调样例做联调。");
        }
        if (!isRefundReady(providerType, settings)) {
            hints.add("补齐退款凭证后，再验证退款预览参数。");
        }
        switch (providerType) {
            case ALIPAY:
                hints.add("确认 `sign` 与 `biz_content` 序列化规则和正式网关一致。");
                break;
            case WECHAT_PAY:
                hints.add("用真实商户私钥验证 `Authorization` 与 `Wechatpay-Serial` 是否匹配。");
                break;
            case STRIPE:
                hints.add("下一步建议直接接真实 Checkout Session 创建和 webhook 事件回放。");
                break;
            case PAYPAL:
                hints.add("下一步建议串起 order create -> approve -> capture 的真实链路。");
                break;
            case UNIONPAY:
                hints.add("下一步建议补银联证书链和前后台通知联调。");
                break;
            default:
                hints.add("下一步建议按预览参数在目标平台控制台或 SDK 中跑一次最小闭环。");
                break;
        }
        return hints;
    }

    private List<StageReadiness> resolveStageReadiness(PaymentProviderType providerType,
                                                       PaymentConfigService.PaymentResolvedSettings settings) {
        List<StageReadiness> stages = new ArrayList<>();
        stages.add(StageReadiness.builder()
            .stageKey("initiation")
            .stageLabel("下单")
            .ready(isInitiationSignatureReady(providerType, settings))
            .checks(resolveStageChecks(providerType, settings, "initiation"))
            .build());
        stages.add(StageReadiness.builder()
            .stageKey("callback")
            .stageLabel("回调")
            .ready(isCallbackVerificationReady(providerType, settings))
            .checks(resolveStageChecks(providerType, settings, "callback"))
            .build());
        stages.add(StageReadiness.builder()
            .stageKey("refund")
            .stageLabel("退款")
            .ready(isRefundReady(providerType, settings))
            .checks(resolveStageChecks(providerType, settings, "refund"))
            .build());
        return stages;
    }

    private List<StageCheck> resolveStageChecks(PaymentProviderType providerType,
                                                PaymentConfigService.PaymentResolvedSettings settings,
                                                String stageKey) {
        List<StageCheck> checks = new ArrayList<>();
        switch (stageKey) {
            case "initiation":
                addCheck(checks, "商户标识", safeString(settings.getMerchantId(), null) != null || safeString(settings.getAppId(), null) != null,
                    "缺少应用ID或商户号");
                if (providerType == PaymentProviderType.ALIPAY || providerType == PaymentProviderType.WECHAT_PAY || providerType == PaymentProviderType.UNIONPAY) {
                    addCheck(checks, "签名私钥", looksLikePemOrBase64Key(settings.getPrivateKey()), "私钥不像真实 PEM/Base64 内容");
                } else {
                    addCheck(checks, "发起凭证", safeString(settings.getPrivateKey(), null) != null || safeString(settings.getApiSecret(), null) != null,
                        "缺少 API Key / Secret / 私钥");
                }
                addCheck(checks, "回跳地址", safeString(settings.getReturnUrl(), null) != null || providerType == PaymentProviderType.WECHAT_PAY,
                    "缺少支付完成返回地址");
                break;
            case "callback":
                String mode = resolveSuggestedVerificationMode(providerType, settings);
                addCheck(checks, "通知地址", safeString(settings.getNotifyUrl(), null) != null, "缺少支付回调地址");
                if ("RSA".equalsIgnoreCase(mode)) {
                    addCheck(checks, "RSA 公钥", looksLikePemOrBase64Key(settings.getPublicKey()), "公钥不像真实 PEM/Base64 内容");
                } else if ("CERTIFICATE".equalsIgnoreCase(mode)) {
                    addCheck(checks, "平台证书", looksLikePemOrBase64Certificate(settings.getPlatformCertificate()), "平台证书不像真实 PEM/Base64 内容");
                    addCheck(checks, "证书序列号", safeString(settings.getCertificateSerialNo(), null) != null, "缺少证书序列号");
                } else if ("HMAC".equalsIgnoreCase(mode)) {
                    addCheck(checks, "Webhook 密钥", safeString(settings.getWebhookSecret(), null) != null || safeString(settings.getApiSecret(), null) != null,
                        "缺少 webhook secret / api secret");
                } else {
                    addCheck(checks, "自定义验签凭证", safeString(settings.getWebhookSecret(), null) != null
                        || safeString(settings.getApiSecret(), null) != null
                        || safeString(settings.getPublicKey(), null) != null, "缺少自定义验签凭证");
                }
                break;
            case "refund":
                addCheck(checks, "退款接口地址", safeString(resolveApiBaseUrl(providerType, settings), null) != null, "缺少支付接口地址");
                if (providerType == PaymentProviderType.ALIPAY || providerType == PaymentProviderType.WECHAT_PAY || providerType == PaymentProviderType.UNIONPAY) {
                    addCheck(checks, "退款签名", looksLikePemOrBase64Key(settings.getPrivateKey()), "退款私钥不像真实 PEM/Base64 内容");
                } else {
                    addCheck(checks, "退款凭证", safeString(settings.getPrivateKey(), null) != null || safeString(settings.getApiSecret(), null) != null,
                        "缺少退款 API 凭证");
                }
                break;
            default:
                break;
        }
        return checks;
    }

    private void addCheck(List<StageCheck> checks, String label, boolean passed, String failureReason) {
        checks.add(StageCheck.builder()
            .label(label)
            .passed(passed)
            .failureReason(passed ? null : failureReason)
            .build());
    }

    private List<String> resolveMissingFields(PaymentProviderType providerType, PaymentConfigService.PaymentResolvedSettings settings) {
        List<String> missingFields = new ArrayList<>();
        switch (providerType) {
            case ALIPAY:
                require(settings.getAppId(), "paymentAppId", missingFields, true);
                require(settings.getMerchantId(), "paymentMerchantId", missingFields, true);
                require(settings.getPrivateKey(), "paymentPrivateKey", missingFields, true);
                require(settings.getPublicKey(), "paymentPublicKey", missingFields, true);
                require(settings.getNotifyUrl(), "paymentNotifyUrl", missingFields, true);
                require(settings.getReturnUrl(), "paymentReturnUrl", missingFields, true);
                break;
            case WECHAT_PAY:
                require(settings.getAppId(), "paymentAppId", missingFields, true);
                require(settings.getMerchantId(), "paymentMerchantId", missingFields, true);
                require(settings.getPrivateKey(), "paymentPrivateKey", missingFields, true);
                require(settings.getNotifyUrl(), "paymentNotifyUrl", missingFields, true);
                require(settings.getApiSecret(), "paymentApiSecret", missingFields, true);
                if (requiresCertificateFields(providerType, settings)) {
                    require(settings.getCertificateSerialNo(), "paymentCertificateSerialNo", missingFields, true);
                    require(settings.getPlatformCertificate(), "paymentPlatformCertificate", missingFields, true);
                }
                break;
            case STRIPE:
                require(settings.getPrivateKey(), "paymentPrivateKey", missingFields, true);
                require(settings.getWebhookSecret(), "paymentWebhookSecret", missingFields, true);
                require(settings.getNotifyUrl(), "paymentNotifyUrl", missingFields, true);
                require(settings.getReturnUrl(), "paymentReturnUrl", missingFields, true);
                break;
            case PAYPAL:
                require(settings.getAppId(), "paymentAppId", missingFields, true);
                require(settings.getPrivateKey(), "paymentPrivateKey", missingFields, true);
                require(settings.getMerchantId(), "paymentMerchantId", missingFields, true);
                require(settings.getWebhookSecret(), "paymentWebhookSecret", missingFields, true);
                require(settings.getNotifyUrl(), "paymentNotifyUrl", missingFields, true);
                require(settings.getReturnUrl(), "paymentReturnUrl", missingFields, true);
                break;
            case UNIONPAY:
                require(settings.getMerchantId(), "paymentMerchantId", missingFields, true);
                require(settings.getPrivateKey(), "paymentPrivateKey", missingFields, true);
                require(settings.getPublicKey(), "paymentPublicKey", missingFields, true);
                require(settings.getNotifyUrl(), "paymentNotifyUrl", missingFields, true);
                require(settings.getReturnUrl(), "paymentReturnUrl", missingFields, true);
                break;
            case PADDLE:
                require(settings.getAppId(), "paymentAppId", missingFields, true);
                require(settings.getPrivateKey(), "paymentPrivateKey", missingFields, true);
                require(settings.getWebhookSecret(), "paymentWebhookSecret", missingFields, true);
                require(settings.getNotifyUrl(), "paymentNotifyUrl", missingFields, true);
                require(settings.getReturnUrl(), "paymentReturnUrl", missingFields, true);
                break;
            case LEMON_SQUEEZY:
                require(settings.getAppId(), "paymentAppId", missingFields, true);
                require(settings.getPrivateKey(), "paymentPrivateKey", missingFields, true);
                require(settings.getWebhookSecret(), "paymentWebhookSecret", missingFields, true);
                require(settings.getNotifyUrl(), "paymentNotifyUrl", missingFields, true);
                require(settings.getReturnUrl(), "paymentReturnUrl", missingFields, true);
                break;
            case ADYEN:
                require(settings.getMerchantId(), "paymentMerchantId", missingFields, true);
                require(settings.getApiSecret(), "paymentApiSecret", missingFields, true);
                require(settings.getWebhookSecret(), "paymentWebhookSecret", missingFields, true);
                require(settings.getNotifyUrl(), "paymentNotifyUrl", missingFields, true);
                require(settings.getReturnUrl(), "paymentReturnUrl", missingFields, true);
                break;
            case MOLLIE:
                require(settings.getPrivateKey(), "paymentPrivateKey", missingFields, true);
                require(settings.getNotifyUrl(), "paymentNotifyUrl", missingFields, true);
                require(settings.getReturnUrl(), "paymentReturnUrl", missingFields, true);
                break;
            case XENDIT:
                require(settings.getMerchantId(), "paymentMerchantId", missingFields, true);
                require(settings.getApiSecret(), "paymentApiSecret", missingFields, true);
                require(settings.getWebhookSecret(), "paymentWebhookSecret", missingFields, true);
                require(settings.getNotifyUrl(), "paymentNotifyUrl", missingFields, true);
                require(settings.getReturnUrl(), "paymentReturnUrl", missingFields, true);
                break;
            case MIDTRANS:
                require(settings.getMerchantId(), "paymentMerchantId", missingFields, true);
                require(settings.getPrivateKey(), "paymentPrivateKey", missingFields, true);
                require(settings.getNotifyUrl(), "paymentNotifyUrl", missingFields, true);
                require(settings.getReturnUrl(), "paymentReturnUrl", missingFields, true);
                break;
            case CUSTOM_WEBHOOK:
            default:
                require(settings.getPrivateKey(), "paymentPrivateKey", missingFields, true);
                require(settings.getNotifyUrl(), "paymentNotifyUrl", missingFields, true);
                require(settings.getApiBaseUrl(), "paymentApiBaseUrl", missingFields, true);
                break;
        }
        return missingFields;
    }

    private boolean requiresCertificateFields(PaymentProviderType providerType,
                                              PaymentConfigService.PaymentResolvedSettings settings) {
        String verificationMode = resolveSuggestedVerificationMode(providerType, settings);
        return "CERTIFICATE".equalsIgnoreCase(verificationMode);
    }

    private Map<String, Object> buildRequestPayload(PaymentProviderType providerType,
                                                    PaymentConfigService.PaymentResolvedSettings settings,
                                                    UserPlanOrder order,
                                                    VipPlan plan,
                                                    UserAccount user) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("providerType", providerType.name());
        payload.put("orderNo", order.getOrderNo());
        payload.put("subject", plan.getName());
        payload.put("username", user.getUsername());
        payload.put("userSlug", user.getSlug());
        payload.put("amountYuan", fenToYuan(order.getAmountFen()));
        payload.put("currency", safeString(settings.getCurrency(), "CNY"));
        payload.put("notifyUrl", settings.getNotifyUrl());
        payload.put("returnUrl", settings.getReturnUrl());
        payload.put("createdAt", formatDateTime(order.getCreatedAt()));

        switch (providerType) {
            case ALIPAY:
                payload.put("gateway", resolveApiBaseUrl(providerType, settings));
                payload.put("method", "alipay.trade.page.pay");
                payload.put("appId", settings.getAppId());
                payload.put("merchantId", settings.getMerchantId());
                Map<String, Object> alipayBizContent = new LinkedHashMap<>();
                alipayBizContent.put("out_trade_no", order.getOrderNo());
                alipayBizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");
                alipayBizContent.put("total_amount", fenToYuan(order.getAmountFen()));
                alipayBizContent.put("subject", plan.getName());
                payload.put("bizContent", alipayBizContent);
                break;
            case WECHAT_PAY:
                payload.put("gateway", resolveApiBaseUrl(providerType, settings));
                payload.put("path", "/v3/pay/transactions/native");
                payload.put("appId", settings.getAppId());
                payload.put("mchId", settings.getMerchantId());
                payload.put("description", plan.getName());
                Map<String, Object> wechatAmount = new LinkedHashMap<>();
                wechatAmount.put("total", order.getAmountFen());
                wechatAmount.put("currency", safeString(settings.getCurrency(), "CNY"));
                payload.put("amount", wechatAmount);
                break;
            case STRIPE:
                payload.put("gateway", resolveApiBaseUrl(providerType, settings));
                payload.put("path", "/v1/checkout/sessions");
                payload.put("secretKeyHint", maskKey(settings.getPrivateKey()));
                Map<String, Object> stripeLineItem = new LinkedHashMap<>();
                stripeLineItem.put("name", plan.getName());
                stripeLineItem.put("amountFen", order.getAmountFen());
                stripeLineItem.put("currency", safeString(settings.getCurrency(), "CNY"));
                payload.put("lineItems", List.of(stripeLineItem));
                break;
            case PAYPAL:
                payload.put("gateway", resolveApiBaseUrl(providerType, settings));
                payload.put("path", "/v2/checkout/orders");
                payload.put("clientIdHint", settings.getAppId());
                payload.put("merchantId", settings.getMerchantId());
                Map<String, Object> paypalAmount = new LinkedHashMap<>();
                paypalAmount.put("currency_code", safeString(settings.getCurrency(), "CNY"));
                paypalAmount.put("value", fenToYuan(order.getAmountFen()));
                Map<String, Object> paypalUnit = new LinkedHashMap<>();
                paypalUnit.put("referenceId", order.getOrderNo());
                paypalUnit.put("description", plan.getName());
                paypalUnit.put("amount", paypalAmount);
                payload.put("purchaseUnits", List.of(paypalUnit));
                break;
            case UNIONPAY:
                payload.put("gateway", resolveApiBaseUrl(providerType, settings));
                payload.put("path", "/gateway/api/appTransReq.do");
                payload.put("merId", settings.getMerchantId());
                payload.put("txnAmt", order.getAmountFen());
                payload.put("txnType", "01");
                payload.put("txnSubType", "01");
                payload.put("currencyCode", "156");
                break;
            case PADDLE:
                payload.put("gateway", resolveApiBaseUrl(providerType, settings));
                payload.put("path", "/transactions");
                payload.put("sellerId", settings.getAppId());
                payload.put("apiKeyHint", maskKey(settings.getPrivateKey()));
                payload.put("webhookSecretHint", maskKey(settings.getWebhookSecret()));
                payload.put("customData", Map.of("orderNo", order.getOrderNo(), "userId", user.getId()));
                break;
            case LEMON_SQUEEZY:
                payload.put("gateway", resolveApiBaseUrl(providerType, settings));
                payload.put("path", "/v1/checkouts");
                payload.put("storeId", settings.getAppId());
                payload.put("apiKeyHint", maskKey(settings.getPrivateKey()));
                payload.put("custom", Map.of("orderNo", order.getOrderNo(), "userSlug", user.getSlug()));
                break;
            case ADYEN:
                payload.put("gateway", resolveApiBaseUrl(providerType, settings));
                payload.put("path", "/checkout/v70/payments");
                payload.put("merchantAccount", settings.getMerchantId());
                payload.put("reference", order.getOrderNo());
                payload.put("returnUrl", settings.getReturnUrl());
                Map<String, Object> adyenAmount = new LinkedHashMap<>();
                adyenAmount.put("currency", safeString(settings.getCurrency(), "CNY"));
                adyenAmount.put("value", order.getAmountFen());
                payload.put("amount", adyenAmount);
                break;
            case MOLLIE:
                payload.put("gateway", resolveApiBaseUrl(providerType, settings));
                payload.put("path", "/v2/payments");
                payload.put("apiKeyHint", maskKey(settings.getPrivateKey()));
                payload.put("sequenceType", "oneoff");
                payload.put("redirectUrl", settings.getReturnUrl());
                payload.put("webhookUrl", settings.getNotifyUrl());
                payload.put("metadata", Map.of("orderNo", order.getOrderNo(), "userId", user.getId()));
                break;
            case XENDIT:
                payload.put("gateway", resolveApiBaseUrl(providerType, settings));
                payload.put("path", "/payment_requests");
                payload.put("merchantId", settings.getMerchantId());
                payload.put("apiKeyHint", maskKey(settings.getPrivateKey()));
                payload.put("webhookSecretHint", maskKey(settings.getWebhookSecret()));
                payload.put("referenceId", order.getOrderNo());
                payload.put("captureMethod", "AUTOMATIC");
                break;
            case MIDTRANS:
                payload.put("gateway", resolveApiBaseUrl(providerType, settings));
                payload.put("path", "/v2/charge");
                payload.put("merchantId", settings.getMerchantId());
                payload.put("serverKeyHint", maskKey(settings.getPrivateKey()));
                payload.put("returnUrl", settings.getReturnUrl());
                payload.put("transactionDetails", Map.of("order_id", order.getOrderNo(), "gross_amount", fenToYuan(order.getAmountFen())));
                break;
            case CUSTOM_WEBHOOK:
            default:
                payload.put("gateway", resolveApiBaseUrl(providerType, settings));
                payload.put("method", "POST");
                Map<String, Object> webhookBody = new LinkedHashMap<>();
                webhookBody.put("orderNo", order.getOrderNo());
                webhookBody.put("subject", plan.getName());
                webhookBody.put("amountFen", order.getAmountFen());
                webhookBody.put("currency", safeString(settings.getCurrency(), "CNY"));
                webhookBody.put("userId", user.getId());
                webhookBody.put("username", user.getUsername());
                payload.put("body", webhookBody);
                break;
        }
        return payload;
    }

    private Map<String, Object> buildCallbackPayload(PaymentProviderType providerType,
                                                     PaymentConfigService.PaymentResolvedSettings settings,
                                                     UserPlanOrder order,
                                                     UserAccount user) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("providerType", providerType.name());
        payload.put("notifyUrl", settings.getNotifyUrl());
        payload.put("orderNo", order.getOrderNo());
        payload.put("status", "PAID");
        payload.put("paidAt", formatDateTime(LocalDateTime.now()));
        payload.put("buyer", user.getUsername());
        payload.put("mock", true);
        payload.put("verified", true);

        switch (providerType) {
            case ALIPAY:
                payload.put("trade_status", "TRADE_SUCCESS");
                payload.put("trade_no", "mock-" + order.getOrderNo());
                break;
            case WECHAT_PAY:
                payload.put("trade_state", "SUCCESS");
                payload.put("transaction_id", "mock-" + order.getOrderNo());
                break;
            case STRIPE:
                payload.put("type", "checkout.session.completed");
                payload.put("event_id", "evt_mock_" + order.getId());
                break;
            case PAYPAL:
                payload.put("event_type", "CHECKOUT.ORDER.APPROVED");
                payload.put("resource_id", "paypal-" + order.getOrderNo());
                break;
            case UNIONPAY:
                payload.put("respCode", "00");
                payload.put("queryId", "unionpay-" + order.getOrderNo());
                payload.put("tn", "tn-" + order.getOrderNo());
                break;
            case PADDLE:
                payload.put("event_type", "transaction.paid");
                payload.put("notification_id", "paddle-" + order.getOrderNo());
                payload.put("data", Map.of("custom_data", Map.of("orderNo", order.getOrderNo())));
                break;
            case LEMON_SQUEEZY:
                payload.put("meta", Map.of("event_name", "order_created", "custom_data", Map.of("orderNo", order.getOrderNo())));
                payload.put("data", Map.of("id", "ls-" + order.getOrderNo(), "attributes", Map.of("status", "paid")));
                break;
            case ADYEN:
                payload.put("eventCode", "AUTHORISATION");
                payload.put("success", "true");
                payload.put("pspReference", "adyen-" + order.getOrderNo());
                payload.put("merchantReference", order.getOrderNo());
                break;
            case MOLLIE:
                payload.put("id", "tr_" + order.getOrderNo());
                payload.put("resource", "payment");
                payload.put("status", "paid");
                payload.put("metadata", Map.of("orderNo", order.getOrderNo()));
                break;
            case XENDIT:
                payload.put("event", "payment.succeeded");
                payload.put("id", "xnd-" + order.getOrderNo());
                payload.put("reference_id", order.getOrderNo());
                payload.put("status", "SUCCEEDED");
                break;
            case MIDTRANS:
                payload.put("transaction_id", "mid-" + order.getOrderNo());
                payload.put("order_id", order.getOrderNo());
                payload.put("transaction_status", "settlement");
                payload.put("fraud_status", "accept");
                break;
            case CUSTOM_WEBHOOK:
            default:
                payload.put("signature", maskKey(settings.getWebhookSecret()));
                break;
        }
        return payload;
    }

    private List<String> resolveReadinessWarnings(PaymentProviderType providerType,
                                                  PaymentConfigService.PaymentResolvedSettings settings) {
        List<String> warnings = new ArrayList<>();
        switch (providerType) {
            case ALIPAY:
                if (!looksLikePemOrBase64Key(settings.getPrivateKey())) {
                    warnings.add("paymentPrivateKey 目前不像 PEM/Base64 RSA 私钥，真实 RSA2 签名可能失败");
                }
                if (!looksLikePemOrBase64Key(settings.getPublicKey())) {
                    warnings.add("paymentPublicKey 目前不像 PEM/Base64 支付宝公钥，真实回调 RSA 验签可能失败");
                }
                break;
            case WECHAT_PAY:
                if (!looksLikePemOrBase64Key(settings.getPrivateKey())) {
                    warnings.add("paymentPrivateKey 目前不像 PEM/Base64 商户私钥，真实微信签名可能失败");
                }
                if ("CERTIFICATE".equalsIgnoreCase(resolveSuggestedVerificationMode(providerType, settings))
                    && !looksLikePemOrBase64Certificate(settings.getPlatformCertificate())) {
                    warnings.add("paymentPlatformCertificate 目前不像 PEM/Base64 平台证书，真实证书验签可能失败");
                }
                break;
            case UNIONPAY:
                if (!looksLikePemOrBase64Key(settings.getPrivateKey())) {
                    warnings.add("paymentPrivateKey 目前不像 PEM/Base64 私钥，银联签名可能失败");
                }
                if (!looksLikePemOrBase64Key(settings.getPublicKey())) {
                    warnings.add("paymentPublicKey 目前不像 PEM/Base64 公钥，银联验签可能失败");
                }
                break;
            default:
                break;
        }
        return warnings;
    }

    private boolean isInitiationSignatureReady(PaymentProviderType providerType,
                                               PaymentConfigService.PaymentResolvedSettings settings) {
        switch (providerType) {
            case ALIPAY:
            case WECHAT_PAY:
            case UNIONPAY:
                return looksLikePemOrBase64Key(settings.getPrivateKey());
            case STRIPE:
            case MOLLIE:
            case PADDLE:
            case LEMON_SQUEEZY:
            case MIDTRANS:
                return safeString(settings.getPrivateKey(), null) != null;
            case PAYPAL:
            case XENDIT:
            case ADYEN:
                return safeString(settings.getApiSecret(), null) != null || safeString(settings.getPrivateKey(), null) != null;
            case CUSTOM_WEBHOOK:
            default:
                return safeString(settings.getPrivateKey(), null) != null || safeString(settings.getApiBaseUrl(), null) != null;
        }
    }

    private boolean isCallbackVerificationReady(PaymentProviderType providerType,
                                                PaymentConfigService.PaymentResolvedSettings settings) {
        String mode = resolveSuggestedVerificationMode(providerType, settings);
        switch (mode) {
            case "RSA":
                return looksLikePemOrBase64Key(settings.getPublicKey());
            case "CERTIFICATE":
                return looksLikePemOrBase64Certificate(settings.getPlatformCertificate());
            case "HMAC":
                return safeString(settings.getWebhookSecret(), null) != null || safeString(settings.getApiSecret(), null) != null;
            case "CUSTOM":
            default:
                return safeString(settings.getWebhookSecret(), null) != null
                    || safeString(settings.getApiSecret(), null) != null
                    || safeString(settings.getPublicKey(), null) != null;
        }
    }

    private boolean isRefundReady(PaymentProviderType providerType,
                                  PaymentConfigService.PaymentResolvedSettings settings) {
        switch (providerType) {
            case ALIPAY:
            case WECHAT_PAY:
                return isInitiationSignatureReady(providerType, settings);
            case STRIPE:
            case MOLLIE:
            case PADDLE:
            case LEMON_SQUEEZY:
            case MIDTRANS:
                return safeString(settings.getPrivateKey(), null) != null;
            case PAYPAL:
            case XENDIT:
            case ADYEN:
                return safeString(settings.getApiSecret(), null) != null || safeString(settings.getPrivateKey(), null) != null;
            case UNIONPAY:
                return looksLikePemOrBase64Key(settings.getPrivateKey()) && safeString(settings.getMerchantId(), null) != null;
            case CUSTOM_WEBHOOK:
            default:
                return safeString(settings.getApiBaseUrl(), null) != null;
        }
    }

    private String buildSupportMessage(boolean enabled,
                                       boolean mockMode,
                                       boolean liveModeReady,
                                       List<String> missingFields,
                                       List<String> readinessWarnings) {
        if (!enabled) {
            return "支付总开关未开启，当前仅保留套餐订单骨架。";
        }
        if (liveModeReady) {
            return mockMode
                ? "当前配置已满足真实支付所需字段，同时仍开启 Mock 模式，建议联调完成后再切换为真实支付。"
                : "当前配置已满足真实支付所需字段，可继续接入真实下单与回调验签。";
        }
        if (!missingFields.isEmpty()) {
            return "当前支付配置仍缺少字段：" + String.join("、", missingFields);
        }
        if (!readinessWarnings.isEmpty()) {
            return "当前支付字段已齐全，但仍存在接入风险：" + String.join("；", readinessWarnings);
        }
        return "当前支付配置仍缺少字段：" + String.join("、", missingFields);
    }

    private boolean looksLikePemOrBase64Key(String value) {
        String normalized = safeString(value, null);
        if (normalized == null) {
            return false;
        }
        if (normalized.contains("BEGIN PRIVATE KEY")
            || normalized.contains("BEGIN RSA PRIVATE KEY")
            || normalized.contains("BEGIN PUBLIC KEY")
            || normalized.contains("BEGIN RSA PUBLIC KEY")) {
            return true;
        }
        String compact = normalized.replaceAll("\\s+", "");
        return compact.length() >= 64 && compact.matches("^[A-Za-z0-9+/=]+$");
    }

    private boolean looksLikePemOrBase64Certificate(String value) {
        String normalized = safeString(value, null);
        if (normalized == null) {
            return false;
        }
        if (normalized.contains("BEGIN CERTIFICATE")) {
            return true;
        }
        String compact = normalized.replaceAll("\\s+", "");
        return compact.length() >= 64 && compact.matches("^[A-Za-z0-9+/=]+$");
    }

    private String resolveProviderLabel(PaymentProviderType providerType) {
        switch (providerType) {
            case WECHAT_PAY:
                return "微信支付";
            case STRIPE:
                return "Stripe";
            case PAYPAL:
                return "PayPal";
            case UNIONPAY:
                return "银联";
            case PADDLE:
                return "Paddle";
            case LEMON_SQUEEZY:
                return "Lemon Squeezy";
            case ADYEN:
                return "Adyen";
            case MOLLIE:
                return "Mollie";
            case XENDIT:
                return "Xendit";
            case MIDTRANS:
                return "Midtrans";
            case CUSTOM_WEBHOOK:
                return "自定义 Webhook";
            case ALIPAY:
            default:
                return "支付宝";
        }
    }

    private String resolveApiBaseUrl(PaymentProviderType providerType, PaymentConfigService.PaymentResolvedSettings settings) {
        String customUrl = safeString(settings.getApiBaseUrl(), null);
        if (customUrl != null) {
            return customUrl;
        }
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

    private String resolveSuggestedVerificationMode(PaymentProviderType providerType,
                                                    PaymentConfigService.PaymentResolvedSettings settings) {
        String configured = safeString(settings.getVerificationMode(), "AUTO");
        if (!"AUTO".equalsIgnoreCase(configured)) {
            return configured.toUpperCase();
        }
        switch (providerType) {
            case ALIPAY:
            case UNIONPAY:
                return "RSA";
            case WECHAT_PAY:
                return safeString(settings.getPlatformCertificate(), null) == null ? "HMAC" : "CERTIFICATE";
            case STRIPE:
            case PAYPAL:
            case PADDLE:
            case LEMON_SQUEEZY:
            case ADYEN:
            case XENDIT:
            case CUSTOM_WEBHOOK:
                return "HMAC";
            case MOLLIE:
            case MIDTRANS:
            default:
                return "CUSTOM";
        }
    }

    private String resolveInitiationMode(PaymentProviderType providerType) {
        switch (providerType) {
            case ALIPAY:
            case UNIONPAY:
                return "REDIRECT_FORM";
            case WECHAT_PAY:
                return "QR_CODE";
            case STRIPE:
            case PAYPAL:
            case PADDLE:
            case LEMON_SQUEEZY:
            case ADYEN:
            case MOLLIE:
            case XENDIT:
            case MIDTRANS:
            case CUSTOM_WEBHOOK:
            default:
                return "API_REQUEST";
        }
    }

    private String resolveRefundMode(PaymentProviderType providerType) {
        switch (providerType) {
            case ALIPAY:
            case WECHAT_PAY:
            case STRIPE:
            case PAYPAL:
            case UNIONPAY:
            case ADYEN:
            case MOLLIE:
            case XENDIT:
            case MIDTRANS:
                return "API_REFUND";
            case PADDLE:
            case LEMON_SQUEEZY:
                return "DASHBOARD_OR_API";
            case CUSTOM_WEBHOOK:
            default:
                return "CUSTOM_CALLBACK";
        }
    }

    private List<String> resolveCapabilityTags(PaymentProviderType providerType) {
        List<String> tags = new ArrayList<>();
        tags.add("统一回调入口");
        switch (providerType) {
            case ALIPAY:
                tags.add("表单跳转");
                tags.add("RSA 验签");
                tags.add("页面回跳");
                break;
            case WECHAT_PAY:
                tags.add("二维码拉起");
                tags.add("证书/HMAC 验签");
                tags.add("异步回调");
                break;
            case STRIPE:
                tags.add("Hosted Checkout");
                tags.add("Webhook 验签");
                tags.add("退款 API");
                break;
            case PAYPAL:
                tags.add("Approve 链接");
                tags.add("Webhook 验签");
                tags.add("退款 API");
                break;
            case UNIONPAY:
                tags.add("网关表单");
                tags.add("证书签名");
                tags.add("前后台通知");
                break;
            case PADDLE:
            case LEMON_SQUEEZY:
                tags.add("订阅/数字商品");
                tags.add("Hosted Checkout");
                tags.add("Webhook 验签");
                break;
            case ADYEN:
                tags.add("聚合收单");
                tags.add("3DS 扩展");
                tags.add("Webhook 验签");
                break;
            case MOLLIE:
                tags.add("欧洲收单");
                tags.add("Hosted Checkout");
                tags.add("退款 API");
                break;
            case XENDIT:
            case MIDTRANS:
                tags.add("东南亚支付");
                tags.add("Hosted Payment Page");
                tags.add("Webhook 验签");
                break;
            case CUSTOM_WEBHOOK:
            default:
                tags.add("自定义网关");
                tags.add("内部收银台");
                tags.add("自定义验签");
                break;
        }
        return tags;
    }

    private List<String> resolveIntegrationSteps(PaymentProviderType providerType) {
        List<String> steps = new ArrayList<>();
        switch (providerType) {
            case ALIPAY:
                steps.add("补应用私钥签名与 biz_content 序列化");
                steps.add("前端提交表单直跳支付宝收银台");
                steps.add("回调侧用支付宝公钥完成 RSA 验签");
                steps.add("退款时接 `alipay.trade.refund`");
                break;
            case WECHAT_PAY:
                steps.add("补商户私钥签名与请求头构造");
                steps.add("解析 Native 下单返回的 code_url");
                steps.add("回调侧按平台证书或 APIv3 Key 验签");
                steps.add("退款时接 `v3/refund/domestic/refunds`");
                break;
            case STRIPE:
                steps.add("创建 Checkout Session 并回填 hosted URL");
                steps.add("回调侧校验 `Stripe-Signature`");
                steps.add("支付成功后同步 session/payment_intent");
                steps.add("退款时接 `refunds.create`");
                break;
            case PAYPAL:
                steps.add("创建 checkout order 并提取 approve 链接");
                steps.add("回调/Webhook 校验 PayPal 签名");
                steps.add("支付成功后 capture order");
                steps.add("退款时接 capture refund API");
                break;
            case UNIONPAY:
                steps.add("补证书签名并提交前台表单");
                steps.add("处理前台回跳和后台通知");
                steps.add("按银联证书体系完成验签");
                steps.add("退款时接退款交易接口");
                break;
            case PADDLE:
                steps.add("创建 transaction 并回填 hosted checkout 链接");
                steps.add("校验 Paddle Webhook Secret");
                steps.add("联通订阅/自动续费映射");
                steps.add("退款时接 Paddle transaction adjustment");
                break;
            case LEMON_SQUEEZY:
                steps.add("创建 checkout 并回填 hosted checkout 链接");
                steps.add("校验 Lemon Squeezy 签名头");
                steps.add("联通订阅与 License/Order 映射");
                steps.add("退款优先走控制台或补官方 API");
                break;
            case ADYEN:
                steps.add("先拉 paymentMethods 再发起 payments");
                steps.add("补 3DS / redirectResult 回跳处理");
                steps.add("校验 HMAC 通知签名");
                steps.add("退款时接 modifications/refunds");
                break;
            case MOLLIE:
                steps.add("创建 payment 并回填 checkoutUrl");
                steps.add("回调后主动查询 payment 状态");
                steps.add("校验 metadata 与来源单映射");
                steps.add("退款时接 refunds API");
                break;
            case XENDIT:
                steps.add("创建 payment request 并回填 hosted payment 链接");
                steps.add("校验 Xendit webhook secret");
                steps.add("支付后根据 referenceId 回写订单");
                steps.add("退款时接 refund / payment token API");
                break;
            case MIDTRANS:
                steps.add("选定 Snap 或 Core API 并生成 token/redirect_url");
                steps.add("校验 transaction_status / fraud_status 回调");
                steps.add("订单完成后根据 order_id 回写");
                steps.add("退款时接 Midtrans refund API");
                break;
            case CUSTOM_WEBHOOK:
            default:
                steps.add("定义内部支付网关请求协议");
                steps.add("约定统一回调签名头与订单号字段");
                steps.add("完成支付后调用统一回调入口");
                steps.add("退款时复用自定义退款回调协议");
                break;
        }
        return steps;
    }

    private void require(String value, String fieldName, List<String> missingFields, boolean required) {
        if (required && safeString(value, null) == null) {
            missingFields.add(fieldName);
        }
    }

    private String maskKey(String value) {
        String normalized = safeString(value, null);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() <= 8) {
            return "****";
        }
        return normalized.substring(0, 4) + "****" + normalized.substring(normalized.length() - 4);
    }

    private String safeString(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private String fenToYuan(Integer fen) {
        BigDecimal amount = BigDecimal.valueOf(fen == null ? 0 : fen)
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return amount.toPlainString();
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : ORDER_TIME_FORMATTER.format(value);
    }

    @Data
    @Builder
    public static class PaymentPreview {
        private String providerType;
        private String providerLabel;
        private boolean enabled;
        private boolean mockEnabled;
        private boolean liveModeReady;
        private String currency;
        private String apiBaseUrl;
        private List<String> missingFields;
        private List<String> readinessWarnings;
        private boolean signatureReady;
        private boolean callbackVerificationReady;
        private boolean refundReady;
        private List<StageReadiness> stageReadiness;
        private List<String> recommendedConfigFields;
        private List<String> nextActionHints;
        private String supportMessage;
        private String verificationMode;
        private String initiationMode;
        private String refundMode;
        private List<String> capabilityTags;
        private List<String> integrationSteps;
        private Map<String, Object> requestPayload;
        private Map<String, Object> callbackPayload;
    }

    @Data
    @Builder
    public static class StageReadiness {
        private String stageKey;
        private String stageLabel;
        private boolean ready;
        private List<StageCheck> checks;
    }

    @Data
    @Builder
    public static class StageCheck {
        private String label;
        private boolean passed;
        private String failureReason;
    }
}
