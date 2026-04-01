package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.entity.UserStatus;
import com.photoexhibition.entity.VipPlan;
import com.photoexhibition.repository.UserAccountRepository;
import com.photoexhibition.repository.UserPlanOrderRepository;
import com.photoexhibition.repository.VipPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VipRenewalService {

    private static final List<String> RENEWABLE_STATUSES = List.of("PAID", "ACTIVE");
    private static final List<String> RENEWAL_CHILD_EXISTS_STATUSES = List.of("CREATED", "PAID", "ACTIVE");

    private final UserPlanOrderRepository userPlanOrderRepository;
    private final UserAccountRepository userAccountRepository;
    private final VipPlanRepository vipPlanRepository;
    private final VipOrderLifecycleService vipOrderLifecycleService;
    private final SystemConfigService systemConfigService;
    private final PaymentGatewayService paymentGatewayService;
    private final PaymentInitiationService paymentInitiationService;

    @Transactional(readOnly = true)
    public Map<String, Object> previewDueRenewals(Integer limit) {
        int safeLimit = limit == null || limit < 1 ? 20 : Math.min(limit, 100);
        LocalDateTime now = LocalDateTime.now();
        List<UserPlanOrder> dueOrders = userPlanOrderRepository
            .findByAutoRenewEnabledTrueAndNextRenewalAtLessThanEqualAndStatusInOrderByNextRenewalAtAscCreatedAtAsc(now, RENEWABLE_STATUSES);

        List<Map<String, Object>> content = dueOrders.stream()
            .limit(safeLimit)
            .map(order -> toRenewalCandidate(order, now))
            .collect(Collectors.toList());

        long activeAutoRenewCount = userPlanOrderRepository.findByAutoRenewEnabledTrueOrderByCreatedAtDesc(org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements();
        long dueCount = dueOrders.size();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("generatedAt", now);
        resp.put("content", content);
        resp.put("limit", safeLimit);
        resp.put("dueCount", dueCount);
        resp.put("returnedCount", content.size());
        resp.put("activeAutoRenewOrderCount", activeAutoRenewCount);
        resp.put("dryRun", true);
        resp.put("supportedStatuses", RENEWABLE_STATUSES);
        resp.put("message", "当前仅提供自动续费候选预演，暂未执行真实扣款与续期建单。");
        return resp;
    }

    @Transactional
    public Map<String, Object> executeDueRenewals(Integer limit) {
        int safeLimit = limit == null || limit < 1 ? 20 : Math.min(limit, 100);
        LocalDateTime now = LocalDateTime.now();
        List<UserPlanOrder> dueOrders = userPlanOrderRepository
            .findByAutoRenewEnabledTrueAndNextRenewalAtLessThanEqualAndStatusInOrderByNextRenewalAtAscCreatedAtAsc(now, RENEWABLE_STATUSES);

        List<Map<String, Object>> executionResults = dueOrders.stream()
            .limit(safeLimit)
            .map(this::createRenewalOrderIfNeeded)
            .collect(Collectors.toList());
        List<Map<String, Object>> createdOrders = executionResults.stream()
            .filter(item -> Boolean.TRUE.equals(item.get("created")))
            .collect(Collectors.toList());
        List<Map<String, Object>> skippedOrders = executionResults.stream()
            .filter(item -> !Boolean.TRUE.equals(item.get("created")))
            .collect(Collectors.toList());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("executedAt", now);
        resp.put("limit", safeLimit);
        resp.put("candidateCount", Math.min(dueOrders.size(), safeLimit));
        resp.put("createdCount", createdOrders.size());
        resp.put("skippedCount", skippedOrders.size());
        resp.put("createdOrders", createdOrders);
        resp.put("skippedOrders", skippedOrders);
        resp.put("message", "已执行自动续费建单骨架：仅创建待支付续费订单，不进行真实扣款。");
        return resp;
    }

    @Scheduled(fixedDelay = 300000, initialDelay = 300000)
    public void scheduledExecuteDueRenewals() {
        if (!systemConfigService.isAutoRenewSchedulerEnabled()) {
            return;
        }
        try {
            Map<String, Object> result = executeDueRenewals(20);
            log.info("自动续费建单任务执行完成: created={}, skipped={}", result.get("createdCount"), result.get("skippedCount"));
        } catch (Exception e) {
            log.warn("自动续费建单任务执行失败", e);
        }
    }

    private Map<String, Object> toRenewalCandidate(UserPlanOrder order, LocalDateTime now) {
        UserAccount user = userAccountRepository.findById(order.getUserId()).orElse(null);
        VipPlan plan = vipPlanRepository.findById(order.getVipPlanId()).orElse(null);
        UserPlanOrder existingRenewalOrder = userPlanOrderRepository
            .findFirstByRenewalSourceOrderIdAndStatusInOrderByCreatedAtDesc(order.getId(), RENEWAL_CHILD_EXISTS_STATUSES)
            .orElse(null);
        String blockingReason = resolveRenewalBlockingReason(order, user, plan, existingRenewalOrder);
        PaymentGatewayService.PaymentPreview paymentPreview = buildRenewalPaymentPreview(order, plan, user, blockingReason);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("orderId", order.getId());
        item.put("orderNo", order.getOrderNo());
        item.put("userId", order.getUserId());
        item.put("username", user != null ? user.getUsername() : null);
        item.put("nickname", user != null ? user.getNickname() : null);
        item.put("userSlug", user != null ? user.getSlug() : null);
        item.put("vipPlanId", order.getVipPlanId());
        item.put("vipPlanCode", plan != null ? plan.getCode() : null);
        item.put("vipPlanName", plan != null ? plan.getName() : null);
        item.put("status", order.getStatus());
        item.put("amountFen", order.getAmountFen());
        item.put("amountYuan", order.getAmountFen() == null ? 0D : order.getAmountFen() / 100D);
        item.put("paymentProviderType", order.getPaymentProviderType());
        item.put("autoRenewEnabled", Boolean.TRUE.equals(order.getAutoRenewEnabled()));
        item.put("nextRenewalAt", order.getNextRenewalAt());
        item.put("renewalSourceOrderId", order.getRenewalSourceOrderId());
        item.put("expireAt", order.getExpireAt());
        item.put("paidAt", order.getPaidAt());
        item.put("daysOverdue", order.getNextRenewalAt() == null ? 0L : Math.max(0L, ChronoUnit.DAYS.between(order.getNextRenewalAt(), now)));
        item.put("hoursOverdue", order.getNextRenewalAt() == null ? 0L : Math.max(0L, ChronoUnit.HOURS.between(order.getNextRenewalAt(), now)));
        item.put("renewalAction", resolveRenewalAction(blockingReason, paymentPreview));
        item.put("renewalBlocked", blockingReason != null);
        item.put("renewalBlockedReason", blockingReason);
        item.put("existingRenewalOrderId", existingRenewalOrder != null ? existingRenewalOrder.getId() : null);
        item.put("existingRenewalOrderNo", existingRenewalOrder != null ? existingRenewalOrder.getOrderNo() : null);
        item.put("existingRenewalOrderStatus", existingRenewalOrder != null ? existingRenewalOrder.getStatus() : null);
        item.put("existingRenewalOrderCreatedAt", existingRenewalOrder != null ? existingRenewalOrder.getCreatedAt() : null);
        item.put("paymentProviderLabel", paymentPreview != null ? paymentPreview.getProviderLabel() : null);
        item.put("paymentMockMode", paymentPreview != null && paymentPreview.isMockEnabled());
        item.put("paymentEnabled", paymentPreview != null && paymentPreview.isEnabled());
        item.put("paymentLiveModeReady", paymentPreview != null && paymentPreview.isLiveModeReady());
        item.put("paymentMissingFields", paymentPreview != null ? paymentPreview.getMissingFields() : null);
        item.put("paymentReadinessWarnings", paymentPreview != null ? paymentPreview.getReadinessWarnings() : null);
        item.put("renewalMessage", resolveRenewalMessage(blockingReason, paymentPreview));
        item.put("remark", order.getRemark());
        return item;
    }

    private Map<String, Object> createRenewalOrderIfNeeded(UserPlanOrder sourceOrder) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("sourceOrderId", sourceOrder.getId());
        item.put("sourceOrderNo", sourceOrder.getOrderNo());
        item.put("userId", sourceOrder.getUserId());
        item.put("vipPlanId", sourceOrder.getVipPlanId());

        UserPlanOrder existingRenewalOrder = userPlanOrderRepository
            .findFirstByRenewalSourceOrderIdAndStatusInOrderByCreatedAtDesc(sourceOrder.getId(), RENEWAL_CHILD_EXISTS_STATUSES)
            .orElse(null);
        VipPlan plan = vipPlanRepository.findById(sourceOrder.getVipPlanId()).orElse(null);
        UserAccount user = userAccountRepository.findById(sourceOrder.getUserId()).orElse(null);
        String blockingReason = resolveRenewalBlockingReason(sourceOrder, user, plan, existingRenewalOrder);
        if (blockingReason != null) {
            item.put("created", false);
            item.put("reason", blockingReason);
            item.put("existingRenewalOrderId", existingRenewalOrder != null ? existingRenewalOrder.getId() : null);
            item.put("existingRenewalOrderNo", existingRenewalOrder != null ? existingRenewalOrder.getOrderNo() : null);
            item.put("existingRenewalOrderStatus", existingRenewalOrder != null ? existingRenewalOrder.getStatus() : null);
            return item;
        }

        UserPlanOrder renewalOrder = new UserPlanOrder();
        renewalOrder.setOrderNo(vipOrderLifecycleService.generateOrderNo());
        renewalOrder.setUserId(sourceOrder.getUserId());
        renewalOrder.setVipPlanId(sourceOrder.getVipPlanId());
        renewalOrder.setAmountFen(sourceOrder.getAmountFen() == null ? (plan.getPriceFen() == null ? 0 : plan.getPriceFen()) : sourceOrder.getAmountFen());
        renewalOrder.setStatus("CREATED");
        renewalOrder.setSource("AUTO_RENEW");
        renewalOrder.setPaymentProviderType(sourceOrder.getPaymentProviderType());
        renewalOrder.setAutoRenewEnabled(true);
        renewalOrder.setRenewalSourceOrderId(sourceOrder.getId());
        renewalOrder.setRemark("AUTO_RENEW_FROM:" + sourceOrder.getOrderNo());
        UserPlanOrder savedRenewal = userPlanOrderRepository.save(renewalOrder);

        sourceOrder.setAutoRenewEnabled(false);
        sourceOrder.setNextRenewalAt(null);
        sourceOrder.setGatewayStatus("RENEWAL_ORDER_CREATED");
        sourceOrder.setRemark(appendRemark(sourceOrder.getRemark(), "已生成续费订单:" + savedRenewal.getOrderNo()));
        userPlanOrderRepository.save(sourceOrder);

        item.put("created", true);
        PaymentGatewayService.PaymentPreview paymentPreview = buildRenewalPaymentPreview(savedRenewal, plan, user, null);
        applyInitiationResult(item, savedRenewal, plan, user, sourceOrder, paymentPreview);
        item.put("reason", resolveExecutionReason(item));
        item.put("createdOrderId", savedRenewal.getId());
        item.put("createdOrderNo", savedRenewal.getOrderNo());
        item.put("username", user.getUsername());
        item.put("nickname", user.getNickname());
        item.put("vipPlanName", plan.getName());
        return item;
    }

    private PaymentGatewayService.PaymentPreview buildRenewalPaymentPreview(UserPlanOrder order,
                                                                           VipPlan plan,
                                                                           UserAccount user,
                                                                           String blockingReason) {
        if (blockingReason != null || order == null || plan == null || user == null) {
            return null;
        }
        try {
            return paymentGatewayService.preview(order, plan, user);
        } catch (Exception e) {
            log.warn("生成自动续费支付预览失败: orderNo={}, error={}", order.getOrderNo(), e.getMessage());
            return null;
        }
    }

    private String resolveRenewalAction(String blockingReason, PaymentGatewayService.PaymentPreview paymentPreview) {
        if (blockingReason != null) {
            return "SKIP_BLOCKED";
        }
        if (paymentPreview == null) {
            return "CREATE_NEW_ORDER";
        }
        if (paymentPreview.isMockEnabled() || paymentPreview.isLiveModeReady()) {
            return "CREATE_AND_INITIATE";
        }
        return "CREATE_NEW_ORDER";
    }

    private String resolveRenewalMessage(String blockingReason, PaymentGatewayService.PaymentPreview paymentPreview) {
        if (blockingReason != null) {
            return blockingReason;
        }
        if (paymentPreview == null) {
            return "可创建新的续费待支付订单";
        }
        if (!paymentPreview.isEnabled()) {
            return "可创建续费待支付订单，但当前支付功能未启用";
        }
        if (paymentPreview.isMockEnabled()) {
            return "可创建续费待支付订单，并直接生成 Mock 支付发起参数";
        }
        if (paymentPreview.isLiveModeReady()) {
            return "可创建续费待支付订单，并同步生成真实支付发起参数";
        }
        return "可创建续费待支付订单，但支付配置仍未就绪";
    }

    private void applyInitiationResult(Map<String, Object> item,
                                       UserPlanOrder renewalOrder,
                                       VipPlan plan,
                                       UserAccount user,
                                       UserPlanOrder sourceOrder,
                                       PaymentGatewayService.PaymentPreview paymentPreview) {
        item.put("paymentProviderLabel", paymentPreview != null ? paymentPreview.getProviderLabel() : null);
        item.put("paymentMockMode", paymentPreview != null && paymentPreview.isMockEnabled());
        item.put("paymentEnabled", paymentPreview != null && paymentPreview.isEnabled());
        item.put("paymentLiveModeReady", paymentPreview != null && paymentPreview.isLiveModeReady());
        item.put("paymentMissingFields", paymentPreview != null ? paymentPreview.getMissingFields() : null);
        item.put("paymentReadinessWarnings", paymentPreview != null ? paymentPreview.getReadinessWarnings() : null);

        if (paymentPreview == null) {
            item.put("initiationAttempted", false);
            item.put("initiationSuccess", false);
            item.put("initiationMessage", "支付预览生成失败，当前仅创建续费待支付订单");
            return;
        }
        if (!paymentPreview.isEnabled()) {
            item.put("initiationAttempted", false);
            item.put("initiationSuccess", false);
            item.put("initiationMessage", "支付功能未启用，当前仅创建续费待支付订单");
            return;
        }
        if (!paymentPreview.isMockEnabled() && !paymentPreview.isLiveModeReady()) {
            item.put("initiationAttempted", false);
            item.put("initiationSuccess", false);
            item.put("initiationMessage", "支付配置未就绪，当前仅创建续费待支付订单");
            return;
        }

        try {
            Map<String, Object> initiation = paymentInitiationService.initiateByOrderId(renewalOrder.getId());
            item.put("initiationAttempted", true);
            item.put("initiationSuccess", true);
            item.put("initiationMessage", initiation.get("message"));
            item.put("initiationActionType", initiation.get("actionType"));
            item.put("initiationLaunchUrl", initiation.get("launchUrl"));
            item.put("initiationRedirect", initiation.get("redirect"));
            item.put("initiationMockMode", initiation.get("mockMode"));
            item.put("initiationProviderType", initiation.get("providerType"));
            sourceOrder.setGatewayStatus("RENEWAL_ORDER_INITIATED");
            sourceOrder.setRemark(appendRemark(sourceOrder.getRemark(), "续费子单已生成支付发起参数:" + renewalOrder.getOrderNo()));
            userPlanOrderRepository.save(sourceOrder);
        } catch (Exception e) {
            log.warn("自动续费支付发起失败: sourceOrderNo={}, renewalOrderNo={}, error={}",
                sourceOrder.getOrderNo(), renewalOrder.getOrderNo(), e.getMessage());
            item.put("initiationAttempted", true);
            item.put("initiationSuccess", false);
            item.put("initiationMessage", e.getMessage());
        }
    }

    private String resolveExecutionReason(Map<String, Object> item) {
        if (!Boolean.TRUE.equals(item.get("created"))) {
            return item.get("reason") == null ? "未创建续费订单" : String.valueOf(item.get("reason"));
        }
        if (!Boolean.TRUE.equals(item.get("initiationAttempted"))) {
            return String.valueOf(item.get("initiationMessage"));
        }
        if (Boolean.TRUE.equals(item.get("initiationSuccess"))) {
            return "已创建续费订单并生成支付发起参数";
        }
        return "已创建续费订单，但支付发起失败: " + item.get("initiationMessage");
    }

    private String resolveRenewalBlockingReason(UserPlanOrder sourceOrder,
                                                UserAccount user,
                                                VipPlan plan,
                                                UserPlanOrder existingRenewalOrder) {
        if (existingRenewalOrder != null) {
            return "已存在待支付或已生效的续费订单";
        }
        if (user == null) {
            return "订单用户不存在";
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            return "用户状态不是 ACTIVE，已阻止自动续费建单";
        }
        if (plan == null) {
            return "VIP 套餐不存在";
        }
        if (!Boolean.TRUE.equals(plan.getEnabled())) {
            return "VIP 套餐已停用，已阻止自动续费建单";
        }
        if (sourceOrder.getAmountFen() == null && (plan.getPriceFen() == null || plan.getPriceFen() <= 0)) {
            return "订单与套餐均缺少有效金额，已阻止自动续费建单";
        }
        return null;
    }

    private String appendRemark(String original, String extra) {
        if (extra == null || extra.isBlank()) {
            return original;
        }
        if (original == null || original.isBlank()) {
            return extra;
        }
        return original + " | " + extra;
    }
}
