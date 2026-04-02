package com.photoexhibition.service;

import com.photoexhibition.config.JwtConfig;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.entity.VipPlan;
import com.photoexhibition.repository.UserAccountRepository;
import com.photoexhibition.repository.UserPlanOrderRepository;
import com.photoexhibition.repository.VipPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserVipService {

    private final JwtConfig jwtConfig;
    private final UserAccountRepository userAccountRepository;
    private final VipPlanRepository vipPlanRepository;
    private final UserPlanOrderRepository userPlanOrderRepository;
    private final PaymentConfigService paymentConfigService;
    private final PaymentGatewayService paymentGatewayService;
    private final PaymentInitiationService paymentInitiationService;
    private final UserStorageService userStorageService;
    private final VipOrderLifecycleService vipOrderLifecycleService;
    private final VipPlanChangePolicyService vipPlanChangePolicyService;

    @Transactional(readOnly = true)
    public Map<String, Object> getOverview(String token) {
        UserAccount user = getCurrentUser(token);
        VipPlan currentPlan = user.getCurrentVipPlanId() == null ? null : vipPlanRepository.findById(user.getCurrentVipPlanId()).orElse(null);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("userId", user.getId());
        resp.put("username", user.getUsername());
        resp.put("nickname", user.getNickname());
        resp.put("storageUsedBytes", defaultLong(user.getStorageUsedBytes()));
        resp.put("storageQuotaBytes", userStorageService.getEffectiveQuotaBytes(user));
        resp.put("baseQuotaBytes", defaultLong(user.getStorageQuotaBytes()));
        resp.put("vipExtraQuotaBytes", defaultLong(user.getVipExtraQuotaBytes()));
        resp.put("currentVipPlanId", user.getCurrentVipPlanId());
        resp.put("vipExpireAt", user.getVipExpireAt());
        resp.put("currentVipPlanName", currentPlan != null ? currentPlan.getName() : null);
        resp.put("currentVipPlanCode", currentPlan != null ? currentPlan.getCode() : null);
        resp.put("currentVipPlanExtraQuotaBytes", currentPlan != null ? defaultLong(currentPlan.getExtraQuotaBytes()) : 0L);
        resp.put("currentVipPlanDurationDays", currentPlan != null ? currentPlan.getDurationDays() : null);
        resp.put("currentVipPlanCategory", currentPlan != null ? currentPlan.getPlanCategory() : null);
        resp.put("currentVipQuotaGrantMode", currentPlan != null ? currentPlan.getQuotaGrantMode() : null);
        PaymentConfigService.PaymentResolvedSettings paymentSettings = paymentConfigService.getResolvedSettings();
        resp.put("paymentEnabled", paymentSettings.isEnabled());
        resp.put("paymentMockEnabled", paymentSettings.isMockEnabled());
        resp.put("paymentProviderType", paymentSettings.getProviderType() == null ? PaymentProviderType.ALIPAY.name() : paymentSettings.getProviderType().name());
        return resp;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listPlans(String token) {
        UserAccount user = getCurrentUser(token);
        VipPlan currentPlan = user.getCurrentVipPlanId() == null ? null : vipPlanRepository.findById(user.getCurrentVipPlanId()).orElse(null);
        List<Map<String, Object>> plans = vipPlanRepository.findAllByOrderBySortOrderAscIdAsc().stream()
            .filter(plan -> Boolean.TRUE.equals(plan.getEnabled()))
            .map(plan -> toPlanMap(plan, user, currentPlan))
            .collect(Collectors.toList());
        return Map.of("plans", plans);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listMyOrders(String token) {
        UserAccount user = getCurrentUser(token);
        List<Map<String, Object>> orders = userPlanOrderRepository.findTop20ByUserIdOrderByCreatedAtDesc(user.getId()).stream()
            .map(order -> toOrderMap(order, user))
            .collect(Collectors.toList());
        return Map.of("orders", orders);
    }

    @Transactional
    public Map<String, Object> createOrder(String token, Long planId) {
        UserAccount user = getCurrentUser(token);
        VipPlan plan = vipPlanRepository.findById(planId)
            .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
            .orElseThrow(() -> new RuntimeException("VIP 套餐不存在或已停用"));
        VipPlan currentPlan = user.getCurrentVipPlanId() == null ? null : vipPlanRepository.findById(user.getCurrentVipPlanId()).orElse(null);
        VipPlanChangePolicyService.PlanChangeDecision decision = vipPlanChangePolicyService.evaluate(
            user,
            currentPlan,
            user.getVipExpireAt(),
            plan,
            LocalDateTime.now()
        );
        if (!decision.isAllowed()) {
            throw new RuntimeException(decision.getReason());
        }

        UserPlanOrder order = new UserPlanOrder();
        order.setOrderNo(vipOrderLifecycleService.generateOrderNo());
        order.setUserId(user.getId());
        order.setVipPlanId(plan.getId());
        order.setAmountFen(decision.getPayableAmountFen());
        order.setOriginalAmountFen(decision.getOriginalAmountFen());
        order.setCreditedAmountFen(decision.getCreditedAmountFen());
        order.setChangeType(decision.getAction());
        order.setSourceVipPlanId(currentPlan != null ? currentPlan.getId() : null);
        order.setPricingDetailJson(String.valueOf(vipPlanChangePolicyService.toMap(decision)));
        order.setStatus("CREATED");
        order.setSource("USER_SELF_SERVICE");
        order.setExpireAt(decision.getEffectiveExpireAt());
        PaymentProviderType providerType = paymentConfigService.getResolvedSettings().getProviderType();
        order.setPaymentProviderType(providerType == null ? PaymentProviderType.ALIPAY.name() : providerType.name());
        if ("RENEWAL".equalsIgnoreCase(decision.getAction())) {
            order.setAutoRenewEnabled(true);
            order.setNextRenewalAt(decision.getEffectiveExpireAt());
        }

        UserPlanOrder saved = userPlanOrderRepository.save(order);
        return toOrderMap(saved, user);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> previewCheckout(String token, Long orderId) {
        UserAccount user = getCurrentUser(token);
        UserPlanOrder order = userPlanOrderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("VIP 订单不存在"));
        if (!Objects.equals(order.getUserId(), user.getId())) {
            throw new RuntimeException("无权访问该订单");
        }
        VipPlan plan = vipPlanRepository.findById(order.getVipPlanId())
            .orElseThrow(() -> new RuntimeException("VIP 套餐不存在"));
        PaymentGatewayService.PaymentPreview preview = paymentGatewayService.preview(order, plan, user);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("order", toOrderMap(order, user));
        resp.put("payment", preview);
        return resp;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> initiateCheckout(String token, Long orderId) {
        UserAccount user = getCurrentUser(token);
        return paymentInitiationService.initiateForUser(orderId, user.getId());
    }

    @Transactional
    public Map<String, Object> mockPayOrder(String token, Long orderId) {
        UserAccount user = getCurrentUser(token);
        UserPlanOrder order = userPlanOrderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("VIP 订单不存在"));
        if (!Objects.equals(order.getUserId(), user.getId())) {
            throw new RuntimeException("无权操作该订单");
        }
        if (!canInitiatePayment(order)) {
            throw new RuntimeException("当前订单状态不可进行支付");
        }
        PaymentConfigService.PaymentResolvedSettings settings = paymentConfigService.getResolvedSettings();
        if (!settings.isMockEnabled()) {
            throw new RuntimeException("当前未开启支付 Mock 模式");
        }
        PaymentProviderType providerType = settings.getProviderType() == null ? PaymentProviderType.ALIPAY : settings.getProviderType();
        UserPlanOrder paidOrder = vipOrderLifecycleService.markOrderPaid(
            order,
            providerType,
            "mock-" + order.getOrderNo(),
            Map.of(
                "orderNo", order.getOrderNo(),
                "status", "PAID",
                "source", "USER_SELF_SERVICE_MOCK"
            ),
            null
        );
        return toOrderMap(paidOrder, user);
    }

    @Transactional
    public Map<String, Object> updateAutoRenew(String token, Long orderId, boolean autoRenewEnabled) {
        UserAccount user = getCurrentUser(token);
        UserPlanOrder order = userPlanOrderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("VIP 订单不存在"));
        if (!Objects.equals(order.getUserId(), user.getId())) {
            throw new RuntimeException("无权操作该订单");
        }
        String status = order.getStatus() == null ? "" : order.getStatus().trim().toUpperCase();
        if (!List.of("CREATED", "PAID", "ACTIVE").contains(status)) {
            throw new RuntimeException("当前订单状态不允许修改自动续费");
        }
        order.setAutoRenewEnabled(autoRenewEnabled);
        if (!autoRenewEnabled) {
            order.setNextRenewalAt(null);
        } else if (order.getExpireAt() != null) {
            order.setNextRenewalAt(order.getExpireAt());
        }
        UserPlanOrder saved = userPlanOrderRepository.save(order);
        return toOrderMap(saved, user);
    }

    private Map<String, Object> toPlanMap(VipPlan plan, UserAccount user, VipPlan currentPlan) {
        VipPlanChangePolicyService.PlanChangeDecision decision = vipPlanChangePolicyService.evaluate(
            user,
            currentPlan,
            user == null ? null : user.getVipExpireAt(),
            plan,
            LocalDateTime.now()
        );
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", plan.getId());
        item.put("code", plan.getCode());
        item.put("name", plan.getName());
        item.put("description", plan.getDescription());
        item.put("extraQuotaBytes", defaultLong(plan.getExtraQuotaBytes()));
        item.put("durationDays", plan.getDurationDays());
        item.put("priceFen", plan.getPriceFen());
        item.put("priceYuan", fenToYuan(plan.getPriceFen()));
        item.put("planCategory", plan.getPlanCategory());
        item.put("quotaGrantMode", plan.getQuotaGrantMode());
        item.put("stackingMode", plan.getStackingMode());
        item.put("enabled", Boolean.TRUE.equals(plan.getEnabled()));
        item.put("sortOrder", plan.getSortOrder());
        item.put("available", decision.isAllowed());
        item.put("purchaseAction", decision.getAction());
        item.put("purchaseHint", decision.getReason());
        item.put("payableAmountFen", decision.getPayableAmountFen());
        item.put("payableAmountYuan", fenToYuan(decision.getPayableAmountFen()));
        item.put("originalAmountFen", decision.getOriginalAmountFen());
        item.put("originalAmountYuan", fenToYuan(decision.getOriginalAmountFen()));
        item.put("creditedAmountFen", decision.getCreditedAmountFen());
        item.put("creditedAmountYuan", fenToYuan(decision.getCreditedAmountFen()));
        item.put("effectiveExpireAt", decision.getEffectiveExpireAt());
        return item;
    }

    private Map<String, Object> toOrderMap(UserPlanOrder order, UserAccount user) {
        VipPlan plan = vipPlanRepository.findById(order.getVipPlanId()).orElse(null);
        UserPlanOrder renewalSourceOrder = order.getRenewalSourceOrderId() == null
            ? null
            : userPlanOrderRepository.findById(order.getRenewalSourceOrderId()).orElse(null);
        UserPlanOrder renewalChildOrder = order.getId() == null
            ? null
            : userPlanOrderRepository.findFirstByRenewalSourceOrderIdOrderByCreatedAtDesc(order.getId()).orElse(null);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", order.getId());
        item.put("orderNo", order.getOrderNo());
        item.put("userId", order.getUserId());
        item.put("username", user.getUsername());
        item.put("nickname", user.getNickname());
        item.put("vipPlanId", order.getVipPlanId());
        item.put("vipPlanName", plan != null ? plan.getName() : null);
        item.put("vipPlanCode", plan != null ? plan.getCode() : null);
        item.put("amountFen", order.getAmountFen());
        item.put("amountYuan", fenToYuan(order.getAmountFen()));
        item.put("originalAmountFen", order.getOriginalAmountFen());
        item.put("originalAmountYuan", fenToYuan(order.getOriginalAmountFen()));
        item.put("creditedAmountFen", order.getCreditedAmountFen());
        item.put("creditedAmountYuan", fenToYuan(order.getCreditedAmountFen()));
        item.put("changeType", order.getChangeType());
        item.put("sourceVipPlanId", order.getSourceVipPlanId());
        item.put("status", order.getStatus());
        item.put("source", order.getSource());
        item.put("paymentProviderType", order.getPaymentProviderType());
        item.put("externalTradeNo", order.getExternalTradeNo());
        item.put("gatewayStatus", order.getGatewayStatus());
        item.put("callbackPayloadJson", order.getCallbackPayloadJson());
        item.put("paymentNotifiedAt", order.getPaymentNotifiedAt());
        item.put("paidAt", order.getPaidAt());
        item.put("cancelledAt", order.getCancelledAt());
        item.put("refundStatus", order.getRefundStatus());
        item.put("refundAmountFen", order.getRefundAmountFen());
        item.put("refundedAt", order.getRefundedAt());
        item.put("autoRenewEnabled", Boolean.TRUE.equals(order.getAutoRenewEnabled()));
        item.put("nextRenewalAt", order.getNextRenewalAt());
        item.put("renewalSourceOrderId", order.getRenewalSourceOrderId());
        item.put("renewalSourceOrderNo", renewalSourceOrder != null ? renewalSourceOrder.getOrderNo() : null);
        item.put("renewalSourceOrderStatus", renewalSourceOrder != null ? renewalSourceOrder.getStatus() : null);
        item.put("renewalChildOrderId", renewalChildOrder != null ? renewalChildOrder.getId() : null);
        item.put("renewalChildOrderNo", renewalChildOrder != null ? renewalChildOrder.getOrderNo() : null);
        item.put("renewalChildOrderStatus", renewalChildOrder != null ? renewalChildOrder.getStatus() : null);
        item.put("canInitiatePayment", canInitiatePayment(order));
        item.put("canToggleAutoRenew", canToggleAutoRenew(order));
        item.put("orderStageLabel", resolveOrderStageLabel(order));
        item.put("renewalChainType", order.getRenewalSourceOrderId() == null ? "PRIMARY" : "RENEWAL_CHILD");
        item.put("expireAt", order.getExpireAt());
        item.put("pricingDetailJson", order.getPricingDetailJson());
        item.put("remark", order.getRemark());
        item.put("createdAt", order.getCreatedAt());
        item.put("updatedAt", order.getUpdatedAt());
        return item;
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private String fenToYuan(Integer fen) {
        return BigDecimal.valueOf(fen == null ? 0 : fen)
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
            .toPlainString();
    }

    private UserAccount getCurrentUser(String token) {
        String username = jwtConfig.extractUsername(token);
        if (username == null || username.trim().isEmpty()) {
            throw new RuntimeException("Token无效");
        }
        return userAccountRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    private boolean canInitiatePayment(UserPlanOrder order) {
        String status = order.getStatus() == null ? "" : order.getStatus().trim().toUpperCase();
        return "CREATED".equals(status);
    }

    private boolean canToggleAutoRenew(UserPlanOrder order) {
        String status = order.getStatus() == null ? "" : order.getStatus().trim().toUpperCase();
        return List.of("CREATED", "PAID", "ACTIVE").contains(status);
    }

    private String resolveOrderStageLabel(UserPlanOrder order) {
        if ("REQUESTED".equalsIgnoreCase(order.getRefundStatus())
            && ("PAID".equalsIgnoreCase(order.getStatus()) || "ACTIVE".equalsIgnoreCase(order.getStatus()))) {
            return "退款处理中";
        }
        if ("FAILED".equalsIgnoreCase(order.getRefundStatus())
            && ("PAID".equalsIgnoreCase(order.getStatus()) || "ACTIVE".equalsIgnoreCase(order.getStatus()))) {
            return "退款失败";
        }
        String status = order.getStatus() == null ? "" : order.getStatus().trim().toUpperCase();
        switch (status) {
            case "CREATED":
                return "待支付";
            case "PAID":
            case "ACTIVE":
                return order.getRenewalSourceOrderId() == null ? "已生效" : "续费已生效";
            case "CANCELLED":
                return "已取消";
            case "REFUNDED":
                return "已退款";
            default:
                return status.isEmpty() ? "未知状态" : status;
        }
    }
}
