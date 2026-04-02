package com.photoexhibition.service;

import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.repository.UserPlanOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.util.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentCallbackService {

    private final UserPlanOrderRepository userPlanOrderRepository;
    private final VipOrderLifecycleService vipOrderLifecycleService;
    private final PaymentConfigService paymentConfigService;
    private final List<PaymentCallbackAdapter> paymentCallbackAdapters;

    @Transactional
    public Map<String, Object> handleNotify(String providerTypeRaw, Map<String, Object> payload) {
        PaymentProviderType providerType = resolveProviderType(providerTypeRaw);
        PaymentConfigService.PaymentResolvedSettings settings = paymentConfigService.getResolvedSettings();
        VerificationResult verification = verifyNotifyPayload(providerType, payload, settings);
        PaymentCallbackAdapter adapter = resolveCallbackAdapter(providerType);
        String orderNo = adapter.extractOrderNo(payload);
        String resolvedOrderNoSource = resolveNotifyOrderNoSource(payload, orderNo);
        if (orderNo == null || orderNo.isBlank()) {
            throw new RuntimeException("支付回调缺少订单号");
        }
        if (!verification.isVerified()) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", false);
            resp.put("recognized", false);
            resp.put("verified", false);
            resp.put("verificationMode", verification.getVerificationMode());
            resp.put("verificationMessage", verification.getMessage());
            resp.put("orderNo", orderNo);
            resp.put("resolvedOrderNoSource", resolvedOrderNoSource);
            return resp;
        }
        UserPlanOrder order = userPlanOrderRepository.findByOrderNo(orderNo)
            .orElseThrow(() -> new RuntimeException("VIP 订单不存在"));
        String status = adapter.extractOrderStatus(payload);
        if ("UNKNOWN".equals(status)) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", true);
            resp.put("orderNo", orderNo);
            resp.put("recognized", false);
            resp.put("verified", true);
            resp.put("verificationMode", verification.getVerificationMode());
            resp.put("verificationMessage", verification.getMessage());
            resp.put("resolvedOrderNoSource", resolvedOrderNoSource);
            resp.put("message", "已接收回调，但当前状态未识别为支付成功");
            return resp;
        }
        UserPlanOrder saved;
        String externalTradeNo = adapter.extractExternalTradeNo(payload);
        LocalDateTime eventTime = extractEventTime(payload);
        if ("PAID".equals(status)) {
            saved = vipOrderLifecycleService.markOrderPaid(order, providerType, externalTradeNo, payload, eventTime);
        } else if ("CANCELLED".equals(status)) {
            saved = vipOrderLifecycleService.markOrderCancelled(order, providerType, externalTradeNo, payload, eventTime);
        } else if ("REFUNDED".equals(status)) {
            saved = vipOrderLifecycleService.markOrderRefunded(
                order,
                providerType,
                externalTradeNo,
                extractRefundAmountFen(payload, order.getAmountFen()),
                payload,
                eventTime
            );
        } else {
            saved = order;
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("recognized", true);
        resp.put("verified", true);
        resp.put("verificationMode", verification.getVerificationMode());
        resp.put("verificationMessage", verification.getMessage());
        resp.put("orderNo", saved.getOrderNo());
        resp.put("resolvedOrderNoSource", resolvedOrderNoSource);
        resp.put("status", saved.getStatus());
        resp.put("externalTradeNo", saved.getExternalTradeNo());
        resp.put("gatewayStatus", saved.getGatewayStatus());
        resp.put("refundStatus", saved.getRefundStatus());
        resp.put("refundAmountFen", saved.getRefundAmountFen());
        return resp;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> previewNotify(String providerTypeRaw, Map<String, Object> payload) {
        PaymentProviderType providerType = resolveProviderType(providerTypeRaw);
        PaymentConfigService.PaymentResolvedSettings settings = paymentConfigService.getResolvedSettings();
        VerificationResult verification = verifyNotifyPayload(providerType, payload, settings);
        PaymentCallbackAdapter adapter = resolveCallbackAdapter(providerType);
        String orderNo = adapter.extractOrderNo(payload);
        String recognizedStatus = adapter.extractOrderStatus(payload);
        String externalTradeNo = adapter.extractExternalTradeNo(payload);
        LocalDateTime eventTime = extractEventTime(payload);
        String predictedLifecycleAction = resolvePredictedLifecycleAction(recognizedStatus);
        boolean wouldUpdateOrder = verification.isVerified()
            && orderNo != null
            && !orderNo.isBlank()
            && !"UNKNOWN".equalsIgnoreCase(recognizedStatus);

        UserPlanOrder order = orderNo == null || orderNo.isBlank()
            ? null
            : userPlanOrderRepository.findByOrderNo(orderNo).orElse(null);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", verification.isVerified() && orderNo != null && !orderNo.isBlank());
        resp.put("preview", true);
        resp.put("providerType", providerType.name());
        resp.put("providerLabel", resolveProviderLabel(providerType));
        resp.put("recognized", !"UNKNOWN".equals(recognizedStatus));
        resp.put("verified", verification.isVerified());
        resp.put("verificationMode", verification.getVerificationMode());
        resp.put("verificationMessage", verification.getMessage());
        resp.put("orderNo", orderNo);
        resp.put("resolvedOrderNoSource", resolveNotifyOrderNoSource(payload, orderNo));
        resp.put("recognizedStatus", recognizedStatus);
        resp.put("externalTradeNo", externalTradeNo);
        resp.put("eventTime", eventTime);
        resp.put("refundAmountFen", extractRefundAmountFen(payload, order == null ? null : order.getAmountFen()));
        resp.put("orderExists", order != null);
        resp.put("currentOrderStatus", order == null ? null : order.getStatus());
        resp.put("predictedLifecycleAction", predictedLifecycleAction);
        resp.put("wouldUpdateOrder", wouldUpdateOrder && order != null);
        resp.put("predictedFinalStatus", resolvePredictedFinalStatus(order, recognizedStatus, verification.isVerified()));
        resp.put("recommendedActions", buildPreviewRecommendedActions(order, orderNo, recognizedStatus, verification.isVerified()));

        if (orderNo == null || orderNo.isBlank()) {
            resp.put("message", "预演完成，但未识别到订单号");
        } else if (!verification.isVerified()) {
            resp.put("message", "预演完成，但验签未通过，不会变更订单状态");
        } else if ("UNKNOWN".equals(recognizedStatus)) {
            resp.put("message", "预演完成，但当前回调状态未识别为支付成功/取消/退款");
        } else if (order == null) {
            resp.put("message", "预演完成，已识别订单号与状态，但本地订单不存在");
        } else {
            resp.put("message", "预演完成，已识别订单号、状态与验签结果，未执行任何落库操作");
        }
        return resp;
    }

    private String resolveNotifyOrderNoSource(Map<String, Object> payload, String resolvedOrderNo) {
        if (payload == null || payload.isEmpty() || resolvedOrderNo == null || resolvedOrderNo.isBlank()) {
            return null;
        }
        if (resolvedOrderNo.equals(stringValue(payload.get("orderNo"), null))) {
            return "orderNo";
        }
        if (resolvedOrderNo.equals(stringValue(payload.get("out_trade_no"), null))) {
            return "out_trade_no";
        }
        if (resolvedOrderNo.equals(stringValue(payload.get("invoice_id"), null))) {
            return "invoice_id";
        }
        if (resolvedOrderNo.equals(stringValue(payload.get("custom_id"), null))) {
            return "custom_id";
        }
        if (resolvedOrderNo.equals(stringValue(payload.get("reference_id"), null))) {
            return "reference_id";
        }
        if (resolvedOrderNo.equals(stringValue(payload.get("merchantReference"), null))) {
            return "merchantReference";
        }
        if (resolvedOrderNo.equals(stringValue(payload.get("order_id"), null))) {
            return "order_id";
        }
        if (resolvedOrderNo.equals(stringValue(payload.get("orderId"), null))) {
            return "orderId";
        }
        if (resolvedOrderNo.equals(stringValue(payload.get("merOrderId"), null))) {
            return "merOrderId";
        }
        if (resolvedOrderNo.equals(stringValue(payload.get("client_reference_id"), null))) {
            return "client_reference_id";
        }
        if (resolvedOrderNo.equals(extractEmbeddedOrderNo(stringValue(payload.get("passback_params"), null)))) {
            return "passback_params";
        }
        if (resolvedOrderNo.equals(extractEmbeddedOrderNo(stringValue(payload.get("attach"), null)))) {
            return "attach";
        }
        if (resolvedOrderNo.equals(nestedString(payload, "resource", "out_trade_no"))) {
            return "resource.out_trade_no";
        }
        if (resolvedOrderNo.equals(nestedString(payload, "resource", "invoice_id"))) {
            return "resource.invoice_id";
        }
        if (resolvedOrderNo.equals(nestedString(payload, "resource", "custom_id"))) {
            return "resource.custom_id";
        }
        if (resolvedOrderNo.equals(nestedString(payload, "resource", "purchase_units", "0", "invoice_id"))) {
            return "resource.purchase_units[0].invoice_id";
        }
        if (resolvedOrderNo.equals(nestedString(payload, "resource", "purchase_units", "0", "custom_id"))) {
            return "resource.purchase_units[0].custom_id";
        }
        if (resolvedOrderNo.equals(nestedString(payload, "data", "object", "client_reference_id"))) {
            return "data.object.client_reference_id";
        }
        if (resolvedOrderNo.equals(nestedString(payload, "data", "object", "metadata", "orderNo"))) {
            return "data.object.metadata.orderNo";
        }
        if (resolvedOrderNo.equals(nestedString(payload, "data", "object", "metadata", "order_no"))) {
            return "data.object.metadata.order_no";
        }
        if (resolvedOrderNo.equals(nestedString(payload, "data", "object", "payment_link_metadata", "orderNo"))) {
            return "data.object.payment_link_metadata.orderNo";
        }
        if (resolvedOrderNo.equals(nestedString(payload, "data", "object", "subscription_details", "metadata", "orderNo"))) {
            return "data.object.subscription_details.metadata.orderNo";
        }
        if (resolvedOrderNo.equals(nestedString(payload, "data", "custom_data", "orderNo"))) {
            return "data.custom_data.orderNo";
        }
        if (resolvedOrderNo.equals(nestedString(payload, "custom_data", "orderNo"))) {
            return "custom_data.orderNo";
        }
        if (resolvedOrderNo.equals(nestedString(payload, "meta", "custom_data", "orderNo"))) {
            return "meta.custom_data.orderNo";
        }
        if (resolvedOrderNo.equals(nestedString(payload, "metadata", "orderNo"))) {
            return "metadata.orderNo";
        }
        if (resolvedOrderNo.equals(nestedString(payload, "resource", "attach"))) {
            return "resource.attach";
        }
        if (resolvedOrderNo.equals(nestedString(payload, "notificationItems", "0", "NotificationRequestItem", "merchantReference"))) {
            return "notificationItems[0].NotificationRequestItem.merchantReference";
        }
        return "AUTO";
    }

    private String resolvePredictedLifecycleAction(String recognizedStatus) {
        if ("PAID".equalsIgnoreCase(recognizedStatus)) {
            return "MARK_PAID";
        }
        if ("CANCELLED".equalsIgnoreCase(recognizedStatus) || "CANCELED".equalsIgnoreCase(recognizedStatus)) {
            return "MARK_CANCELLED";
        }
        if ("REFUNDED".equalsIgnoreCase(recognizedStatus)) {
            return "MARK_REFUNDED";
        }
        return "NONE";
    }

    private String resolvePredictedFinalStatus(UserPlanOrder order, String recognizedStatus, boolean verified) {
        if (!verified || recognizedStatus == null || recognizedStatus.isBlank() || "UNKNOWN".equalsIgnoreCase(recognizedStatus)) {
            return order == null ? null : order.getStatus();
        }
        return recognizedStatus;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> handleReturn(String providerTypeRaw, Map<String, String> queryParams) {
        PaymentProviderType providerType = resolveProviderType(providerTypeRaw);
        Map<String, String> safeQueryParams = queryParams == null ? Map.of() : queryParams;
        String orderNo = extractOrderNoFromReturn(providerType, safeQueryParams);
        UserPlanOrder order = orderNo == null || orderNo.isBlank()
            ? null
            : userPlanOrderRepository.findByOrderNo(orderNo).orElse(null);
        UserPlanOrder renewalSourceOrder = order == null || order.getRenewalSourceOrderId() == null
            ? null
            : userPlanOrderRepository.findById(order.getRenewalSourceOrderId()).orElse(null);
        UserPlanOrder renewalChildOrder = order == null || order.getId() == null
            ? null
            : userPlanOrderRepository.findFirstByRenewalSourceOrderIdOrderByCreatedAtDesc(order.getId()).orElse(null);
        Map<String, Object> resp = new LinkedHashMap<>();
        String normalizedStatus = order == null || order.getStatus() == null ? null : order.getStatus().trim().toUpperCase();
        boolean terminal = normalizedStatus != null && List.of("PAID", "ACTIVE", "CANCELLED", "CANCELED", "REFUNDED").contains(normalizedStatus);
        resp.put("success", order != null);
        resp.put("providerType", providerType.name());
        resp.put("providerLabel", resolveProviderLabel(providerType));
        resp.put("orderNo", orderNo);
        resp.put("resolvedOrderNoSource", resolveReturnOrderNoSource(providerType, safeQueryParams, orderNo));
        resp.put("status", order != null ? order.getStatus() : null);
        resp.put("gatewayStatus", order != null ? order.getGatewayStatus() : null);
        resp.put("externalTradeNo", order != null ? order.getExternalTradeNo() : null);
        resp.put("paymentNotifiedAt", order != null ? order.getPaymentNotifiedAt() : null);
        resp.put("paidAt", order != null ? order.getPaidAt() : null);
        resp.put("cancelledAt", order != null ? order.getCancelledAt() : null);
        resp.put("refundStatus", order != null ? order.getRefundStatus() : null);
        resp.put("refundAmountFen", order != null ? order.getRefundAmountFen() : null);
        resp.put("refundedAt", order != null ? order.getRefundedAt() : null);
        resp.put("autoRenewEnabled", order != null && Boolean.TRUE.equals(order.getAutoRenewEnabled()));
        resp.put("nextRenewalAt", order != null ? order.getNextRenewalAt() : null);
        resp.put("renewalSourceOrderId", order != null ? order.getRenewalSourceOrderId() : null);
        resp.put("renewalSourceOrderNo", renewalSourceOrder != null ? renewalSourceOrder.getOrderNo() : null);
        resp.put("renewalSourceOrderStatus", renewalSourceOrder != null ? renewalSourceOrder.getStatus() : null);
        resp.put("renewalChildOrderId", renewalChildOrder != null ? renewalChildOrder.getId() : null);
        resp.put("renewalChildOrderNo", renewalChildOrder != null ? renewalChildOrder.getOrderNo() : null);
        resp.put("renewalChildOrderStatus", renewalChildOrder != null ? renewalChildOrder.getStatus() : null);
        resp.put("orderStageLabel", order != null ? resolveOrderStageLabel(order) : null);
        resp.put("renewalChainType", order != null ? (order.getRenewalSourceOrderId() == null ? "PRIMARY" : "RENEWAL_CHILD") : null);
        resp.put("canInitiatePayment", order != null && canInitiatePayment(order));
        resp.put("canToggleAutoRenew", order != null && canToggleAutoRenew(order));
        resp.put("expireAt", order != null ? order.getExpireAt() : null);
        resp.put("remark", order != null ? order.getRemark() : null);
        resp.put("createdAt", order != null ? order.getCreatedAt() : null);
        resp.put("updatedAt", order != null ? order.getUpdatedAt() : null);
        resp.put("returnQuery", safeQueryParams);
        resp.put("terminal", terminal);
        resp.put("suggestedPollIntervalSeconds", terminal ? 0 : 3);
        resp.put("recommendedActions", buildRecommendedActions(order, orderNo, terminal));
        resp.put("message", order != null ? "支付返回页骨架已接收，可由前端继续轮询订单状态" : "未找到对应订单");
        resp.put("paid", order != null && ("PAID".equalsIgnoreCase(order.getStatus()) || "ACTIVE".equalsIgnoreCase(order.getStatus())));
        return resp;
    }

    private String extractOrderNoFromReturn(PaymentProviderType providerType, Map<String, String> queryParams) {
        PaymentCallbackAdapter adapter = resolveCallbackAdapter(providerType);
        String adapterOrderNo = adapter.extractReturnOrderNo(queryParams == null ? Map.of() : queryParams);
        Map<String, Object> payload = new LinkedHashMap<>();
        if (queryParams != null) {
            payload.putAll(queryParams);
        }
        return firstNonBlank(
            adapterOrderNo,
            stringValue(payload.get("orderNo"), null),
            adapter.extractOrderNo(payload),
            stringValue(payload.get("outTradeNo"), null),
            stringValue(payload.get("merchantReference"), null),
            queryValue(queryParams, "data", "custom_data", "orderNo"),
            queryValue(queryParams, "custom_data", "orderNo"),
            queryValue(queryParams, "meta", "custom_data", "orderNo"),
            queryValue(queryParams, "metadata", "orderNo"),
            queryValue(queryParams, "metadata", "order_no")
        );
    }

    private String resolveReturnOrderNoSource(PaymentProviderType providerType, Map<String, String> queryParams, String resolvedOrderNo) {
        if (resolvedOrderNo == null || resolvedOrderNo.isBlank() || queryParams == null || queryParams.isEmpty()) {
            return null;
        }
        if (resolvedOrderNo.equals(queryParams.get("orderNo"))) {
            return "orderNo";
        }
        if (resolvedOrderNo.equals(queryParams.get("out_trade_no"))) {
            return "out_trade_no";
        }
        if (resolvedOrderNo.equals(queryParams.get("invoice_id"))) {
            return "invoice_id";
        }
        if (resolvedOrderNo.equals(queryParams.get("custom_id"))) {
            return "custom_id";
        }
        if (resolvedOrderNo.equals(queryParams.get("reference_id"))) {
            return "reference_id";
        }
        if (resolvedOrderNo.equals(queryParams.get("merchantReference"))) {
            return "merchantReference";
        }
        if (resolvedOrderNo.equals(queryParams.get("client_reference_id"))) {
            return "client_reference_id";
        }
        if (resolvedOrderNo.equals(queryParams.get("order_id"))) {
            return "order_id";
        }
        if (resolvedOrderNo.equals(queryParams.get("orderId"))) {
            return "orderId";
        }
        if (resolvedOrderNo.equals(queryParams.get("merOrderId"))) {
            return "merOrderId";
        }
        if (resolvedOrderNo.equals(extractEmbeddedOrderNo(queryParams.get("passback_params")))) {
            return "passback_params";
        }
        if (resolvedOrderNo.equals(extractEmbeddedOrderNo(queryParams.get("attach")))) {
            return "attach";
        }
        if (resolvedOrderNo.equals(queryValue(queryParams, "data", "custom_data", "orderNo"))) {
            return "data.custom_data.orderNo";
        }
        if (resolvedOrderNo.equals(queryValue(queryParams, "custom_data", "orderNo"))) {
            return "custom_data.orderNo";
        }
        if (resolvedOrderNo.equals(queryValue(queryParams, "meta", "custom_data", "orderNo"))) {
            return "meta.custom_data.orderNo";
        }
        if (resolvedOrderNo.equals(queryValue(queryParams, "metadata", "orderNo"))) {
            return "metadata.orderNo";
        }
        if (resolvedOrderNo.equals(queryValue(queryParams, "metadata", "order_no"))) {
            return "metadata.order_no";
        }
        return providerType.name() + "_AUTO";
    }

    private List<String> buildRecommendedActions(UserPlanOrder order, String orderNo, boolean terminal) {
        List<String> actions = new java.util.ArrayList<>();
        if (order == null) {
            actions.add("检查第三方返回参数中的订单号是否正确");
            actions.add("确认支付回调是否已到达并完成订单回写");
            actions.add("必要时前往超级管理员的 API 测试工具做回调预演");
            return actions;
        }
        if (!terminal) {
            actions.add("继续轮询订单状态，等待异步回调完成");
            actions.add("检查支付平台回调地址、验签配置与网关交易号回写");
        }
        if (order.getRefundStatus() != null
            && "REQUESTED".equalsIgnoreCase(order.getRefundStatus())
            && ("PAID".equalsIgnoreCase(order.getStatus()) || "ACTIVE".equalsIgnoreCase(order.getStatus()))) {
            actions.add("当前订单处于退款处理中，可继续等待退款回调或在后台人工确认结果");
        }
        if (order.getRefundStatus() != null
            && "FAILED".equalsIgnoreCase(order.getRefundStatus())
            && ("PAID".equalsIgnoreCase(order.getStatus()) || "ACTIVE".equalsIgnoreCase(order.getStatus()))) {
            actions.add("当前订单退款失败，可在后台重新发起退款或核对退款配置后重试");
        }
        if (Boolean.TRUE.equals(canToggleAutoRenew(order))) {
            actions.add("可在会员中心调整自动续费状态");
        }
        if (Boolean.TRUE.equals(canInitiatePayment(order))) {
            actions.add("如订单仍未完成，可返回会员中心重新发起支付");
        }
        if (orderNo != null && !orderNo.isBlank()) {
            actions.add("可按订单号 " + orderNo + " 在后台继续排查");
        }
        return actions;
    }

    private List<String> buildPreviewRecommendedActions(UserPlanOrder order,
                                                        String orderNo,
                                                        String recognizedStatus,
                                                        boolean verified) {
        List<String> actions = new java.util.ArrayList<>();
        if (orderNo == null || orderNo.isBlank()) {
            actions.add("先补齐回调中的订单号字段，再继续验签与状态联调");
            actions.add("优先使用平台示例负载，确认订单号字段名是否匹配");
            return actions;
        }
        if (!verified) {
            actions.add("先修正验签密钥、证书或 Header，避免真实回调被拒绝");
        }
        if ("UNKNOWN".equalsIgnoreCase(recognizedStatus)) {
            actions.add("补充该平台的状态字段或事件类型，确保能识别支付/退款/取消");
        }
        if (order == null) {
            actions.add("先确认本地是否存在该订单号，必要时检查环境库是否一致");
        } else {
            actions.add("可继续命中真实回调入口，验证订单状态是否会按预期回写");
            actions.add("也可打开支付结果页或超管订单页，按订单号继续排查");
        }
        return actions;
    }

    private PaymentCallbackAdapter resolveCallbackAdapter(PaymentProviderType providerType) {
        return paymentCallbackAdapters.stream()
            .filter(adapter -> adapter.supports(providerType))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("未找到支付回调适配器: " + providerType));
    }

    private VerificationResult verifyNotifyPayload(PaymentProviderType providerType,
                                                   Map<String, Object> payload,
                                                   PaymentConfigService.PaymentResolvedSettings settings) {
        String mode = resolveVerificationMode(providerType, settings);
        if (Boolean.TRUE.equals(payload.get("mock")) && settings.isMockEnabled()) {
            return VerificationResult.ok(mode, "Mock 模式已放行支付回调");
        }
        if (Boolean.TRUE.equals(payload.get("verified")) || Boolean.TRUE.equals(payload.get("signatureVerified"))) {
            return VerificationResult.ok(mode, "回调已由上游网关适配器完成验签");
        }

        switch (mode) {
            case "HMAC":
                return verifyHmac(providerType, payload, settings);
            case "RSA":
                return verifyRsa(providerType, payload, settings);
            case "CERTIFICATE":
                return verifyCertificate(payload, settings);
            case "CUSTOM":
                return verifyCustom(payload, settings);
            case "AUTO":
            default:
                return VerificationResult.fail("AUTO", "无法自动推断可用验签方式，请补充支付密钥或显式指定验签模式");
        }
    }

    private String resolveVerificationMode(PaymentProviderType providerType,
                                           PaymentConfigService.PaymentResolvedSettings settings) {
        String configured = stringValue(settings.getVerificationMode(), "AUTO");
        if (!"AUTO".equalsIgnoreCase(configured)) {
            return configured.toUpperCase();
        }
        switch (providerType) {
            case ALIPAY:
            case UNIONPAY:
                return settings.getPublicKey() == null || settings.getPublicKey().isBlank() ? "AUTO" : "RSA";
            case WECHAT_PAY:
                if (settings.getPlatformCertificate() != null && !settings.getPlatformCertificate().isBlank()) {
                    return "CERTIFICATE";
                }
                return settings.getApiSecret() == null || settings.getApiSecret().isBlank() ? "AUTO" : "HMAC";
            case STRIPE:
            case PAYPAL:
            case PADDLE:
            case LEMON_SQUEEZY:
            case ADYEN:
            case XENDIT:
            case CUSTOM_WEBHOOK:
            default:
                return settings.getWebhookSecret() == null || settings.getWebhookSecret().isBlank() ? "AUTO" : "HMAC";
        }
    }

    private VerificationResult verifyHmac(PaymentProviderType providerType,
                                          Map<String, Object> payload,
                                          PaymentConfigService.PaymentResolvedSettings settings) {
        String secret = firstNonBlank(settings.getWebhookSecret(), settings.getApiSecret());
        if (secret == null) {
            return VerificationResult.fail("HMAC", "缺少 paymentWebhookSecret 或 paymentApiSecret，无法执行 HMAC 验签");
        }
        String rawSignature = firstNonBlank(
            stringValue(payload.get("signature"), null),
            stringValue(payload.get("sign"), null),
            stringValue(payload.get("webhookSecret"), null),
            headerString(payload, "stripe-signature", "paypal-transmission-sig", "x-signature", "x-pay-signature", "x-webhook-signature", "wechatpay-signature")
        );
        if (rawSignature == null) {
            return VerificationResult.fail("HMAC", "回调缺少 signature/sign/Header 签名字段");
        }
        List<String> candidateSignatures = extractHmacCandidates(rawSignature);
        String digest = DigestUtils.md5DigestAsHex(secret.getBytes(StandardCharsets.UTF_8));
        for (String signingContent : resolveHmacSigningContents(providerType, payload, rawSignature)) {
            if (signingContent == null || signingContent.isBlank()) {
                continue;
            }
            String sha256Hex = hmacSha256Hex(secret, signingContent);
            String sha256Base64 = hmacSha256Base64(secret, signingContent);
            for (String candidate : candidateSignatures) {
                if (candidate == null || candidate.isBlank()) {
                    continue;
                }
                if (secret.equals(candidate) || digest.equalsIgnoreCase(candidate)
                    || sha256Hex.equalsIgnoreCase(candidate) || sha256Base64.equals(candidate)) {
                    return VerificationResult.ok("HMAC", "HMAC 验签通过");
                }
            }
        }
        return VerificationResult.fail("HMAC", "HMAC 验签失败，请检查 webhook secret 或网关签名算法");
    }

    private List<String> resolveHmacSigningContents(PaymentProviderType providerType,
                                                    Map<String, Object> payload,
                                                    String rawSignature) {
        List<String> candidates = new ArrayList<>();
        String rawBody = stringValue(payload.get("rawBody"), null);
        if (rawBody != null && !rawBody.isBlank()) {
            if (providerType == PaymentProviderType.STRIPE) {
                String timestamp = firstNonBlank(
                    extractNamedSignaturePart(rawSignature, "t"),
                    headerString(payload, "stripe-timestamp", "x-signature-timestamp")
                );
                if (timestamp != null && !timestamp.isBlank()) {
                    candidates.add(timestamp + "." + rawBody);
                }
            }
            candidates.add(rawBody);
        }
        candidates.add(canonicalizeForSignature(payload));
        return candidates;
    }

    private String extractNamedSignaturePart(String rawSignature, String key) {
        if (rawSignature == null || rawSignature.isBlank() || key == null || key.isBlank()) {
            return null;
        }
        for (String part : rawSignature.split(",")) {
            String token = part == null ? "" : part.trim();
            int equalsIndex = token.indexOf('=');
            if (equalsIndex <= 0 || equalsIndex >= token.length() - 1) {
                continue;
            }
            String currentKey = token.substring(0, equalsIndex).trim();
            if (key.equalsIgnoreCase(currentKey)) {
                return token.substring(equalsIndex + 1).trim();
            }
        }
        return null;
    }

    private List<String> extractHmacCandidates(String rawSignature) {
        if (rawSignature == null || rawSignature.isBlank()) {
            return List.of();
        }
        List<String> candidates = new ArrayList<>();
        for (String part : rawSignature.split(",")) {
            String token = part == null ? "" : part.trim();
            if (token.isEmpty()) {
                continue;
            }
            int equalsIndex = token.indexOf('=');
            String key = equalsIndex > 0 ? token.substring(0, equalsIndex).trim() : "";
            if (equalsIndex > 0
                && equalsIndex < token.length() - 1
                && key.matches("(?i)^[a-z][a-z0-9_-]{0,20}$")) {
                String value = token.substring(equalsIndex + 1).trim();
                if (!key.equalsIgnoreCase("t")) {
                    candidates.add(value);
                }
            } else {
                candidates.add(token);
            }
        }
        return candidates;
    }

    private String canonicalizeForSignature(Map<String, Object> payload) {
        StringBuilder builder = new StringBuilder();
        appendCanonicalValue(builder, sanitizeSignaturePayload(payload));
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    private Object sanitizeSignaturePayload(Object value) {
        if (value instanceof Map) {
            Map<String, Object> source = (Map<String, Object>) value;
            Map<String, Object> sanitized = new LinkedHashMap<>();
            List<String> keys = new ArrayList<>(source.keySet());
            keys.sort(Comparator.naturalOrder());
            for (String key : keys) {
                if (key == null) {
                    continue;
                }
                String normalized = key.trim().toLowerCase();
                if (normalized.equals("_headers")
                    || normalized.equals("signature")
                    || normalized.equals("sign")
                    || normalized.equals("verified")
                    || normalized.equals("signatureverified")
                    || normalized.equals("certificateverified")
                    || normalized.equals("webhooksecret")) {
                    continue;
                }
                sanitized.put(key, sanitizeSignaturePayload(source.get(key)));
            }
            return sanitized;
        }
        if (value instanceof List) {
            List<?> source = (List<?>) value;
            List<Object> sanitized = new ArrayList<>(source.size());
            for (Object item : source) {
                sanitized.add(sanitizeSignaturePayload(item));
            }
            return sanitized;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private void appendCanonicalValue(StringBuilder builder, Object value) {
        if (value instanceof Map) {
            builder.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(entry.getKey()).append(':');
                appendCanonicalValue(builder, entry.getValue());
            }
            builder.append('}');
            return;
        }
        if (value instanceof List) {
            builder.append('[');
            boolean first = true;
            for (Object item : (List<?>) value) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                appendCanonicalValue(builder, item);
            }
            builder.append(']');
            return;
        }
        builder.append(value == null ? "null" : String.valueOf(value));
    }

    private String hmacSha256Hex(String secret, String canonicalPayload) {
        byte[] bytes = hmacSha256(secret, canonicalPayload);
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            builder.append(String.format("%02x", current));
        }
        return builder.toString();
    }

    private String hmacSha256Base64(String secret, String canonicalPayload) {
        return Base64.getEncoder().encodeToString(hmacSha256(secret, canonicalPayload));
    }

    private byte[] hmacSha256(String secret, String canonicalPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(canonicalPayload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("HMAC 验签计算失败", e);
        }
    }

    private VerificationResult verifyRsa(PaymentProviderType providerType,
                                         Map<String, Object> payload,
                                         PaymentConfigService.PaymentResolvedSettings settings) {
        if (settings.getPublicKey() == null || settings.getPublicKey().isBlank()) {
            return VerificationResult.fail("RSA", "缺少 paymentPublicKey，无法执行 RSA 验签");
        }
        if (Boolean.TRUE.equals(payload.get("signatureVerified"))) {
            return VerificationResult.ok("RSA", "RSA 验签结果由上游适配器确认通过");
        }
        String rawSignature = firstNonBlank(
            stringValue(payload.get("signature"), null),
            stringValue(payload.get("sign"), null),
            stringValue(payload.get("signatureValue"), null),
            headerString(payload, "x-signature", "x-pay-signature", "x-rsa-signature", "signature", "sign")
        );
        if (rawSignature == null || rawSignature.isBlank()) {
            return VerificationResult.fail("RSA", "回调缺少 RSA 签名字段");
        }
        String signingContent = firstNonBlank(
            stringValue(payload.get("signaturePayload"), null),
            stringValue(payload.get("signingPayload"), null),
            stringValue(payload.get("signingContent"), null),
            stringValue(payload.get("canonicalPayload"), null),
            stringValue(payload.get("rawBody"), null),
            stringValue(payload.get("body"), null)
        );
        if (signingContent == null || signingContent.isBlank()) {
            signingContent = resolveRsaSigningContent(providerType, payload);
        }
        try {
            PublicKey publicKey = parseRsaPublicKey(settings.getPublicKey());
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(signingContent.getBytes(StandardCharsets.UTF_8));
            for (String candidate : extractHmacCandidates(rawSignature)) {
                byte[] signatureBytes = decodeSignatureBytes(candidate);
                if (signatureBytes != null && verifier.verify(signatureBytes)) {
                    return VerificationResult.ok("RSA", "RSA 验签通过");
                }
                verifier.initVerify(publicKey);
                verifier.update(signingContent.getBytes(StandardCharsets.UTF_8));
            }
            return VerificationResult.fail("RSA", "RSA 验签失败，请检查公钥、签名原文或签名算法");
        } catch (Exception e) {
            return VerificationResult.fail("RSA", "RSA 验签异常: " + e.getMessage());
        }
    }

    private String resolveRsaSigningContent(PaymentProviderType providerType, Map<String, Object> payload) {
        if (providerType == PaymentProviderType.ALIPAY) {
            String alipayContent = buildAlipaySigningContent(payload);
            if (alipayContent != null && !alipayContent.isBlank()) {
                return alipayContent;
            }
        }
        return canonicalizeForSignature(payload);
    }

    private String buildAlipaySigningContent(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        List<String> pairs = new ArrayList<>();
        List<String> keys = new ArrayList<>(payload.keySet());
        keys.sort(String::compareTo);
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            String normalized = key.trim().toLowerCase();
            if (normalized.equals("sign")
                || normalized.equals("signature")
                || normalized.equals("sign_type")
                || normalized.equals("_headers")
                || normalized.equals("verified")
                || normalized.equals("signatureverified")
                || normalized.equals("rawbody")
                || normalized.equals("body")
                || normalized.equals("signaturepayload")
                || normalized.equals("signingpayload")
                || normalized.equals("signingcontent")
                || normalized.equals("canonicalpayload")) {
                continue;
            }
            Object value = payload.get(key);
            if (value == null) {
                continue;
            }
            String stringValue = stringifyAlipayValue(value);
            if (stringValue == null || stringValue.isBlank()) {
                continue;
            }
            pairs.add(key + "=" + stringValue);
        }
        return String.join("&", pairs);
    }

    @SuppressWarnings("unchecked")
    private String stringifyAlipayValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map || value instanceof List) {
            return canonicalizeJsonLikeValue(value);
        }
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private String canonicalizeJsonLikeValue(Object value) {
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            List<String> keys = new ArrayList<>(map.keySet());
            keys.sort(String::compareTo);
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            for (String key : keys) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append('"').append(escapeJson(key)).append('"').append(':')
                    .append(canonicalizeJsonLikeValue(map.get(key)));
            }
            return builder.append('}').toString();
        }
        if (value instanceof List) {
            StringBuilder builder = new StringBuilder("[");
            boolean first = true;
            for (Object item : (List<?>) value) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(canonicalizeJsonLikeValue(item));
            }
            return builder.append(']').toString();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value == null) {
            return "null";
        }
        return "\"" + escapeJson(String.valueOf(value)) + "\"";
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }

    private PublicKey parseRsaPublicKey(String rawKey) throws Exception {
        String normalized = rawKey == null ? "" : rawKey.trim();
        normalized = normalized
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("-----BEGIN RSA PUBLIC KEY-----", "")
            .replace("-----END RSA PUBLIC KEY-----", "")
            .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    private byte[] decodeSignatureBytes(String rawSignature) {
        if (rawSignature == null) {
            return null;
        }
        String normalized = rawSignature.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(normalized);
        } catch (IllegalArgumentException ignored) {
        }
        if (normalized.matches("(?i)^[0-9a-f]+$") && normalized.length() % 2 == 0) {
            byte[] bytes = new byte[normalized.length() / 2];
            for (int i = 0; i < normalized.length(); i += 2) {
                bytes[i / 2] = (byte) Integer.parseInt(normalized.substring(i, i + 2), 16);
            }
            return bytes;
        }
        return null;
    }

    private VerificationResult verifyCertificate(Map<String, Object> payload,
                                                 PaymentConfigService.PaymentResolvedSettings settings) {
        if (settings.getPlatformCertificate() == null || settings.getPlatformCertificate().isBlank()) {
            return VerificationResult.fail("CERTIFICATE", "缺少 paymentPlatformCertificate，无法执行证书验签");
        }
        String serialNo = firstNonBlank(
            stringValue(payload.get("serialNo"), null),
            headerString(payload, "wechatpay-serial", "x-certificate-serial", "x-serial-no")
        );
        if (settings.getCertificateSerialNo() != null
            && !settings.getCertificateSerialNo().isBlank()
            && serialNo != null
            && !settings.getCertificateSerialNo().equalsIgnoreCase(serialNo)) {
            return VerificationResult.fail("CERTIFICATE", "回调证书序列号与配置不匹配");
        }
        if (Boolean.TRUE.equals(payload.get("certificateVerified"))) {
            return VerificationResult.ok("CERTIFICATE", "证书验签结果由上游适配器确认通过");
        }
        String rawSignature = firstNonBlank(
            stringValue(payload.get("signature"), null),
            stringValue(payload.get("sign"), null),
            headerString(payload, "wechatpay-signature", "x-signature", "x-pay-signature")
        );
        if (rawSignature == null || rawSignature.isBlank()) {
            return VerificationResult.fail("CERTIFICATE", "回调缺少证书签名字段");
        }
        String signingContent = resolveCertificateSigningContent(payload);
        try {
            PublicKey publicKey = parseCertificatePublicKey(settings.getPlatformCertificate());
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(signingContent.getBytes(StandardCharsets.UTF_8));
            for (String candidate : extractHmacCandidates(rawSignature)) {
                byte[] signatureBytes = decodeSignatureBytes(candidate);
                if (signatureBytes != null && verifier.verify(signatureBytes)) {
                    return VerificationResult.ok("CERTIFICATE", "证书验签通过");
                }
                verifier.initVerify(publicKey);
                verifier.update(signingContent.getBytes(StandardCharsets.UTF_8));
            }
            return VerificationResult.fail("CERTIFICATE", "证书验签失败，请检查平台证书、签名原文或序列号");
        } catch (Exception e) {
            return VerificationResult.fail("CERTIFICATE", "证书验签异常: " + e.getMessage());
        }
    }

    private String resolveCertificateSigningContent(Map<String, Object> payload) {
        String explicit = firstNonBlank(
            stringValue(payload.get("signaturePayload"), null),
            stringValue(payload.get("signingPayload"), null),
            stringValue(payload.get("signingContent"), null),
            stringValue(payload.get("canonicalPayload"), null)
        );
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        String timestamp = headerString(payload, "wechatpay-timestamp", "x-signature-timestamp");
        String nonce = headerString(payload, "wechatpay-nonce", "x-signature-nonce");
        String body = firstNonBlank(
            stringValue(payload.get("rawBody"), null),
            stringValue(payload.get("body"), null)
        );
        if (timestamp != null && nonce != null && body != null) {
            return timestamp + "\n" + nonce + "\n" + body + "\n";
        }
        return canonicalizeForSignature(payload);
    }

    private PublicKey parseCertificatePublicKey(String rawCertificate) throws Exception {
        String trimmed = rawCertificate == null ? "" : rawCertificate.trim();
        if (trimmed.contains("BEGIN PUBLIC KEY") || trimmed.contains("BEGIN RSA PUBLIC KEY")) {
            return parseRsaPublicKey(trimmed);
        }
        byte[] certBytes = trimmed.getBytes(StandardCharsets.UTF_8);
        if (!trimmed.contains("BEGIN CERTIFICATE")) {
            certBytes = Base64.getDecoder().decode(trimmed.replaceAll("\\s+", ""));
        }
        X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
            .generateCertificate(new ByteArrayInputStream(certBytes));
        return certificate.getPublicKey();
    }

    private VerificationResult verifyCustom(Map<String, Object> payload,
                                            PaymentConfigService.PaymentResolvedSettings settings) {
        String token = firstNonBlank(settings.getWebhookSecret(), settings.getApiSecret(), settings.getPublicKey());
        if (token == null) {
            return VerificationResult.fail("CUSTOM", "缺少自定义回调验签凭证");
        }
        String provided = firstNonBlank(
            stringValue(payload.get("signature"), null),
            stringValue(payload.get("token"), null),
            stringValue(payload.get("secret"), null),
            headerString(payload, "x-signature", "x-auth-token", "authorization", "x-webhook-token")
        );
        if (token.equals(provided)) {
            return VerificationResult.ok("CUSTOM", "自定义回调签名校验通过");
        }
        return VerificationResult.fail("CUSTOM", "自定义回调签名不匹配");
    }

    private PaymentProviderType resolveProviderType(String raw) {
        try {
            return PaymentProviderType.valueOf((raw == null ? "" : raw.trim()).toUpperCase());
        } catch (Exception e) {
            throw new RuntimeException("不支持的支付平台");
        }
    }

    private String extractOrderNo(PaymentProviderType providerType, Map<String, Object> payload) {
        switch (providerType) {
            case ALIPAY:
                return firstNonBlank(
                    stringValue(payload.get("out_trade_no"), null),
                    stringValue(payload.get("orderNo"), null)
                );
            case WECHAT_PAY:
                return firstNonBlank(
                    stringValue(payload.get("out_trade_no"), null),
                    nestedString(payload, "resource", "out_trade_no"),
                    stringValue(payload.get("orderNo"), null)
                );
            case STRIPE:
                return firstNonBlank(
                    stringValue(payload.get("client_reference_id"), null),
                    nestedString(payload, "data", "object", "client_reference_id"),
                    nestedString(payload, "data", "object", "metadata", "orderNo"),
                    stringValue(payload.get("orderNo"), null)
                );
            case PAYPAL:
                return firstNonBlank(
                    stringValue(payload.get("invoice_id"), null),
                    nestedString(payload, "resource", "invoice_id"),
                    nestedString(payload, "resource", "custom_id"),
                    stringValue(payload.get("orderNo"), null)
                );
            case UNIONPAY:
                return firstNonBlank(
                    stringValue(payload.get("orderNo"), null),
                    stringValue(payload.get("orderId"), null),
                    stringValue(payload.get("merOrderId"), null)
                );
            case PADDLE:
                return firstNonBlank(
                    stringValue(payload.get("orderNo"), null),
                    nestedString(payload, "data", "custom_data", "orderNo"),
                    nestedString(payload, "custom_data", "orderNo")
                );
            case LEMON_SQUEEZY:
                return firstNonBlank(
                    stringValue(payload.get("orderNo"), null),
                    nestedString(payload, "meta", "custom_data", "orderNo")
                );
            case ADYEN:
                return firstNonBlank(
                    stringValue(payload.get("merchantReference"), null),
                    nestedString(payload, "notificationItems", "0", "NotificationRequestItem", "merchantReference"),
                    stringValue(payload.get("orderNo"), null)
                );
            case MOLLIE:
                return firstNonBlank(
                    nestedString(payload, "metadata", "orderNo"),
                    stringValue(payload.get("orderNo"), null)
                );
            case XENDIT:
                return firstNonBlank(
                    stringValue(payload.get("reference_id"), null),
                    nestedString(payload, "data", "reference_id"),
                    stringValue(payload.get("orderNo"), null)
                );
            case MIDTRANS:
                return firstNonBlank(
                    stringValue(payload.get("order_id"), null),
                    stringValue(payload.get("orderNo"), null)
                );
            case CUSTOM_WEBHOOK:
            default:
                return stringValue(payload.get("orderNo"), null);
        }
    }

    private String extractExternalTradeNo(PaymentProviderType providerType, Map<String, Object> payload) {
        switch (providerType) {
            case ALIPAY:
                return firstNonBlank(
                    stringValue(payload.get("trade_no"), null),
                    stringValue(payload.get("id"), null)
                );
            case WECHAT_PAY:
                return firstNonBlank(
                    stringValue(payload.get("transaction_id"), null),
                    nestedString(payload, "resource", "transaction_id"),
                    stringValue(payload.get("id"), null)
                );
            case STRIPE:
                return firstNonBlank(
                    stringValue(payload.get("event_id"), null),
                    stringValue(payload.get("id"), null),
                    nestedString(payload, "data", "object", "payment_intent")
                );
            case PAYPAL:
                return firstNonBlank(
                    stringValue(payload.get("resource_id"), null),
                    nestedString(payload, "resource", "id"),
                    stringValue(payload.get("id"), null)
                );
            case UNIONPAY:
                return firstNonBlank(
                    stringValue(payload.get("queryId"), null),
                    stringValue(payload.get("traceNo"), null),
                    stringValue(payload.get("tn"), null)
                );
            case PADDLE:
                return firstNonBlank(
                    stringValue(payload.get("notification_id"), null),
                    nestedString(payload, "data", "id"),
                    stringValue(payload.get("id"), null)
                );
            case LEMON_SQUEEZY:
                return firstNonBlank(
                    nestedString(payload, "data", "id"),
                    stringValue(payload.get("id"), null)
                );
            case ADYEN:
                return firstNonBlank(
                    stringValue(payload.get("pspReference"), null),
                    nestedString(payload, "notificationItems", "0", "NotificationRequestItem", "pspReference"),
                    stringValue(payload.get("id"), null)
                );
            case MOLLIE:
                return firstNonBlank(
                    stringValue(payload.get("id"), null),
                    nestedString(payload, "payment", "id")
                );
            case XENDIT:
                return firstNonBlank(
                    stringValue(payload.get("id"), null),
                    nestedString(payload, "data", "id")
                );
            case MIDTRANS:
                return firstNonBlank(
                    stringValue(payload.get("transaction_id"), null),
                    stringValue(payload.get("id"), null)
                );
            case CUSTOM_WEBHOOK:
            default:
                return stringValue(payload.get("tradeNo"), null);
        }
    }

    private String extractOrderStatus(PaymentProviderType providerType, Map<String, Object> payload) {
        switch (providerType) {
            case ALIPAY:
                String alipayStatus = stringValue(payload.get("trade_status"), null);
                if ("TRADE_SUCCESS".equalsIgnoreCase(alipayStatus)) return "PAID";
                if ("TRADE_CLOSED".equalsIgnoreCase(alipayStatus)) return "CANCELLED";
                if ("REFUND_SUCCESS".equalsIgnoreCase(alipayStatus)) return "REFUNDED";
                return "UNKNOWN";
            case WECHAT_PAY:
                String wechatStatus = firstNonBlank(stringValue(payload.get("trade_state"), null), stringValue(payload.get("refund_status"), null));
                if ("SUCCESS".equalsIgnoreCase(wechatStatus)) return "PAID";
                if ("CLOSED".equalsIgnoreCase(wechatStatus) || "REVOKED".equalsIgnoreCase(wechatStatus)) return "CANCELLED";
                if ("REFUND".equalsIgnoreCase(wechatStatus) || "SUCCESS".equalsIgnoreCase(stringValue(payload.get("refund_status"), null))) return "REFUNDED";
                return "UNKNOWN";
            case STRIPE:
                String stripeType = stringValue(payload.get("type"), null);
                if ("checkout.session.completed".equalsIgnoreCase(stripeType) || "payment_intent.succeeded".equalsIgnoreCase(stripeType)) return "PAID";
                if ("charge.refunded".equalsIgnoreCase(stripeType) || "refund.updated".equalsIgnoreCase(stripeType)) return "REFUNDED";
                if ("payment_intent.canceled".equalsIgnoreCase(stripeType)) return "CANCELLED";
                return "UNKNOWN";
            case PAYPAL:
                String paypalType = stringValue(payload.get("event_type"), null);
                if ("CHECKOUT.ORDER.APPROVED".equalsIgnoreCase(paypalType) || "PAYMENT.CAPTURE.COMPLETED".equalsIgnoreCase(paypalType)) return "PAID";
                if ("PAYMENT.CAPTURE.REFUNDED".equalsIgnoreCase(paypalType)) return "REFUNDED";
                if ("CHECKOUT.ORDER.VOIDED".equalsIgnoreCase(paypalType)) return "CANCELLED";
                return "UNKNOWN";
            case UNIONPAY:
                if ("00".equalsIgnoreCase(stringValue(payload.get("respCode"), null))) return "PAID";
                if ("REFUNDED".equalsIgnoreCase(stringValue(payload.get("status"), null))) return "REFUNDED";
                if ("CANCELLED".equalsIgnoreCase(stringValue(payload.get("status"), null))) return "CANCELLED";
                return "UNKNOWN";
            case PADDLE:
                String paddleType = stringValue(payload.get("event_type"), null);
                if ("transaction.paid".equalsIgnoreCase(paddleType)) return "PAID";
                if ("transaction.canceled".equalsIgnoreCase(paddleType)) return "CANCELLED";
                if ("transaction.refunded".equalsIgnoreCase(paddleType)) return "REFUNDED";
                return "UNKNOWN";
            case LEMON_SQUEEZY:
                String lemonStatus = nestedString(payload, "data", "attributes", "status");
                if ("paid".equalsIgnoreCase(lemonStatus)) return "PAID";
                if ("refunded".equalsIgnoreCase(lemonStatus)) return "REFUNDED";
                if ("cancelled".equalsIgnoreCase(lemonStatus) || "canceled".equalsIgnoreCase(lemonStatus)) return "CANCELLED";
                return "UNKNOWN";
            case ADYEN:
                String adyenEvent = firstNonBlank(
                    stringValue(payload.get("eventCode"), null),
                    nestedString(payload, "notificationItems", "0", "NotificationRequestItem", "eventCode")
                );
                String adyenSuccess = firstNonBlank(
                    stringValue(payload.get("success"), null),
                    nestedString(payload, "notificationItems", "0", "NotificationRequestItem", "success")
                );
                if ("AUTHORISATION".equalsIgnoreCase(adyenEvent) && "true".equalsIgnoreCase(adyenSuccess)) return "PAID";
                if ("REFUND".equalsIgnoreCase(adyenEvent) && "true".equalsIgnoreCase(adyenSuccess)) return "REFUNDED";
                if ("CANCELLATION".equalsIgnoreCase(adyenEvent) && "true".equalsIgnoreCase(adyenSuccess)) return "CANCELLED";
                return "UNKNOWN";
            case MOLLIE:
                String mollieStatus = stringValue(payload.get("status"), null);
                if ("paid".equalsIgnoreCase(mollieStatus)) return "PAID";
                if ("refunded".equalsIgnoreCase(mollieStatus)) return "REFUNDED";
                if ("canceled".equalsIgnoreCase(mollieStatus) || "cancelled".equalsIgnoreCase(mollieStatus) || "expired".equalsIgnoreCase(mollieStatus)) return "CANCELLED";
                return "UNKNOWN";
            case XENDIT:
                String xenditEvent = firstNonBlank(stringValue(payload.get("event"), null), stringValue(payload.get("status"), null));
                if ("payment.succeeded".equalsIgnoreCase(xenditEvent) || "SUCCEEDED".equalsIgnoreCase(xenditEvent) || "PAID".equalsIgnoreCase(xenditEvent)) return "PAID";
                if ("payment.refunded".equalsIgnoreCase(xenditEvent) || "REFUNDED".equalsIgnoreCase(xenditEvent)) return "REFUNDED";
                if ("payment.cancelled".equalsIgnoreCase(xenditEvent) || "CANCELLED".equalsIgnoreCase(xenditEvent) || "FAILED".equalsIgnoreCase(xenditEvent)) return "CANCELLED";
                return "UNKNOWN";
            case MIDTRANS:
                String midtransStatus = stringValue(payload.get("transaction_status"), null);
                if ("settlement".equalsIgnoreCase(midtransStatus) || "capture".equalsIgnoreCase(midtransStatus)) return "PAID";
                if ("refund".equalsIgnoreCase(midtransStatus) || "partial_refund".equalsIgnoreCase(midtransStatus)) return "REFUNDED";
                if ("cancel".equalsIgnoreCase(midtransStatus) || "deny".equalsIgnoreCase(midtransStatus) || "expire".equalsIgnoreCase(midtransStatus)) return "CANCELLED";
                return "UNKNOWN";
            case CUSTOM_WEBHOOK:
            default:
                String status = stringValue(payload.get("status"), null);
                if ("PAID".equalsIgnoreCase(status)) return "PAID";
                if ("REFUNDED".equalsIgnoreCase(status)) return "REFUNDED";
                if ("CANCELLED".equalsIgnoreCase(status) || "CANCELED".equalsIgnoreCase(status)) return "CANCELLED";
                return "UNKNOWN";
        }
    }

    private LocalDateTime extractEventTime(Map<String, Object> payload) {
        String raw = firstNonBlank(
            stringValue(payload.get("paidAt"), null),
            stringValue(payload.get("cancelledAt"), null),
            stringValue(payload.get("refundedAt"), null),
            stringValue(payload.get("transaction_time"), null),
            stringValue(payload.get("settlement_time"), null),
            stringValue(payload.get("time_created"), null),
            stringValue(payload.get("created_at"), null),
            stringValue(payload.get("updated_at"), null),
            nestedString(payload, "resource", "success_time"),
            nestedString(payload, "resource", "update_time"),
            nestedString(payload, "data", "object", "created"),
            nestedString(payload, "data", "attributes", "created_at"),
            nestedString(payload, "data", "attributes", "updated_at"),
            nestedString(payload, "notificationItems", "0", "NotificationRequestItem", "eventDate")
        );
        if (raw == null) {
            return null;
        }
        try {
            if (raw.matches("^\\d+$")) {
                long epochSeconds = Long.parseLong(raw);
                return LocalDateTime.ofEpochSecond(epochSeconds, 0, java.time.ZoneOffset.UTC);
            }
            return LocalDateTime.parse(raw.replace(" ", "T"));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer extractRefundAmountFen(Map<String, Object> payload, Integer fallback) {
        Object value = firstNonNull(
            payload.get("refundAmountFen"),
            payload.get("refund_amount_fen"),
            payload.get("refund_fee"),
            nestedObject(payload, "resource", "refund_amount"),
            nestedObject(payload, "data", "attributes", "refund_amount")
        );
        if (value == null) {
            return fallback;
        }
        try {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            String text = String.valueOf(value).trim();
            if (text.matches("^\\d+$")) {
                return Integer.parseInt(text);
            }
            return (int) Math.round(Double.parseDouble(text) * 100);
        } catch (Exception e) {
            return fallback;
        }
    }

    private String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) {
                String normalizedItem = stringValue(item, null);
                if (normalizedItem != null) {
                    return normalizedItem;
                }
            }
            return fallback;
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
                String normalizedItem = stringValue(java.lang.reflect.Array.get(value, index), null);
                if (normalizedItem != null) {
                    return normalizedItem;
                }
            }
            return fallback;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    @SuppressWarnings("unchecked")
    private String nestedString(Map<String, Object> payload, String... path) {
        Object value = nestedObject(payload, path);
        return stringValue(value, null);
    }

    @SuppressWarnings("unchecked")
    private Object nestedObject(Map<String, Object> payload, String... path) {
        Object current = payload;
        for (String segment : path) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(segment);
                continue;
            }
            if (current instanceof java.util.List && segment != null && segment.matches("\\d+")) {
                int index = Integer.parseInt(segment);
                java.util.List<?> list = (java.util.List<?>) current;
                if (index < 0 || index >= list.size()) {
                    return null;
                }
                current = list.get(index);
                continue;
            }
            return null;
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private String headerString(Map<String, Object> payload, String... names) {
        Object headersObject = payload.get("_headers");
        if (!(headersObject instanceof Map)) {
            return null;
        }
        Map<String, Object> headers = (Map<String, Object>) headersObject;
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                    String value = stringValue(entry.getValue(), null);
                    if (value != null) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private String queryValue(Map<String, String> queryParams, String... path) {
        if (queryParams == null || queryParams.isEmpty() || path == null || path.length == 0) {
            return null;
        }
        String dotPath = String.join(".", path);
        StringBuilder bracketPathBuilder = new StringBuilder(path[0]);
        for (int index = 1; index < path.length; index++) {
            bracketPathBuilder.append('[').append(path[index]).append(']');
        }
        return firstNonBlank(
            queryParams.get(dotPath),
            queryParams.get(bracketPathBuilder.toString())
        );
    }

    private String extractEmbeddedOrderNo(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (String pair : raw.split("&")) {
            if (pair == null || pair.isBlank()) {
                continue;
            }
            int equalsIndex = pair.indexOf('=');
            String rawKey = equalsIndex >= 0 ? pair.substring(0, equalsIndex) : pair;
            String rawValue = equalsIndex >= 0 ? pair.substring(equalsIndex + 1) : "";
            String decodedKey = urlDecode(rawKey);
            if (decodedKey == null) {
                continue;
            }
            if (List.of("orderNo", "order_no", "out_trade_no", "merchant_order_no").contains(decodedKey)) {
                return stringValue(urlDecode(rawValue), null);
            }
        }
        String decoded = urlDecode(raw);
        if (decoded != null && !decoded.equals(raw)) {
            return extractEmbeddedOrderNo(decoded);
        }
        return null;
    }

    private String urlDecode(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return raw;
        }
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.trim().isEmpty()) {
                return candidate.trim();
            }
        }
        return null;
    }

    private Object firstNonNull(Object... candidates) {
        for (Object candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private boolean canInitiatePayment(UserPlanOrder order) {
        String status = order == null || order.getStatus() == null ? "" : order.getStatus().trim().toUpperCase();
        return "CREATED".equals(status);
    }

    private boolean canToggleAutoRenew(UserPlanOrder order) {
        String status = order == null || order.getStatus() == null ? "" : order.getStatus().trim().toUpperCase();
        return java.util.List.of("CREATED", "PAID", "ACTIVE").contains(status);
    }

    private String resolveOrderStageLabel(UserPlanOrder order) {
        String status = order == null || order.getStatus() == null ? "" : order.getStatus().trim().toUpperCase();
        String refundStatus = order == null || order.getRefundStatus() == null ? "" : order.getRefundStatus().trim().toUpperCase();
        switch (status) {
            case "CREATED":
                return "待支付";
            case "PAID":
            case "ACTIVE":
                if ("REQUESTED".equals(refundStatus)) {
                    return "退款处理中";
                }
                if ("FAILED".equals(refundStatus)) {
                    return "退款失败";
                }
                return order.getRenewalSourceOrderId() == null ? "已生效" : "续费已生效";
            case "CANCELLED":
            case "CANCELED":
                return "已取消";
            case "REFUNDED":
                return "已退款";
            default:
                return status.isEmpty() ? "未知状态" : status;
        }
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

    @lombok.Value(staticConstructor = "of")
    private static class VerificationResult {
        boolean verified;
        String verificationMode;
        String message;

        static VerificationResult ok(String verificationMode, String message) {
            return of(true, verificationMode, message);
        }

        static VerificationResult fail(String verificationMode, String message) {
            return of(false, verificationMode, message);
        }
    }
}
