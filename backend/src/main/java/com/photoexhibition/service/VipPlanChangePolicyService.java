package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.VipPlan;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class VipPlanChangePolicyService {

    public PlanChangeDecision evaluate(UserAccount user,
                                       VipPlan currentPlan,
                                       LocalDateTime currentExpireAt,
                                       VipPlan targetPlan,
                                       LocalDateTime now) {
        LocalDateTime effectiveNow = now == null ? LocalDateTime.now() : now;
        if (targetPlan == null || !Boolean.TRUE.equals(targetPlan.getEnabled())) {
            throw new RuntimeException("VIP 套餐不存在或已停用");
        }

        if (!isSupportedPlan(targetPlan)) {
            return blocked("当前版本仅支持标准固定时长容量套餐");
        }
        if (currentPlan != null && !isSupportedPlan(currentPlan)) {
            return blocked("当前已有套餐属于活动/永久容量等扩展类型，暂不支持在线更换");
        }

        boolean hasActiveCurrentPlan = user != null
            && currentPlan != null
            && currentExpireAt != null
            && currentExpireAt.isAfter(effectiveNow);

        if (!hasActiveCurrentPlan) {
            return purchase(targetPlan, effectiveNow);
        }

        long currentQuotaBytes = Math.max(0L, currentPlan.getExtraQuotaBytes() == null ? 0L : currentPlan.getExtraQuotaBytes());
        long targetQuotaBytes = Math.max(0L, targetPlan.getExtraQuotaBytes() == null ? 0L : targetPlan.getExtraQuotaBytes());
        int currentDurationDays = Math.max(1, currentPlan.getDurationDays() == null ? 30 : currentPlan.getDurationDays());
        int targetDurationDays = Math.max(1, targetPlan.getDurationDays() == null ? 30 : targetPlan.getDurationDays());
        int targetPriceFen = Math.max(0, targetPlan.getPriceFen() == null ? 0 : targetPlan.getPriceFen());

        if (targetQuotaBytes == currentQuotaBytes) {
            return PlanChangeDecision.builder()
                .allowed(true)
                .action("RENEWAL")
                .reason("相同容量可直接续费，按所选套餐延长时长")
                .targetPlanId(targetPlan.getId())
                .currentPlanId(currentPlan.getId())
                .payableAmountFen(targetPriceFen)
                .originalAmountFen(targetPriceFen)
                .creditedAmountFen(0)
                .effectiveExpireAt(currentExpireAt.plusDays(targetDurationDays))
                .build();
        }

        if (targetQuotaBytes < currentQuotaBytes) {
            return blocked("当前仅支持续费或升级更大容量，暂不支持降级");
        }

        if (targetDurationDays != currentDurationDays) {
            return blocked("升级容量仅支持相同时长套餐；不同容量且不同时长暂不支持更换");
        }

        BigDecimal remainingSeconds = BigDecimal.valueOf(Math.max(0L, Duration.between(effectiveNow, currentExpireAt).getSeconds()));
        BigDecimal cycleSeconds = BigDecimal.valueOf((long) currentDurationDays * 24L * 60L * 60L);
        BigDecimal remainingCycles = cycleSeconds.signum() <= 0
            ? BigDecimal.ZERO
            : remainingSeconds.divide(cycleSeconds, 6, RoundingMode.HALF_UP);
        BigDecimal quotaRatio = currentQuotaBytes <= 0 || targetQuotaBytes <= 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(currentQuotaBytes).divide(BigDecimal.valueOf(targetQuotaBytes), 6, RoundingMode.HALF_UP);
        int creditFen = BigDecimal.valueOf(targetPriceFen)
            .multiply(quotaRatio)
            .multiply(remainingCycles)
            .setScale(0, RoundingMode.HALF_UP)
            .min(BigDecimal.valueOf(targetPriceFen))
            .intValue();
        int payableFen = Math.max(0, targetPriceFen - creditFen);

        return PlanChangeDecision.builder()
            .allowed(true)
            .action("UPGRADE")
            .reason("相同时长下可升级到更大容量，旧套餐按剩余时长与新套餐容量比例抵扣")
            .targetPlanId(targetPlan.getId())
            .currentPlanId(currentPlan.getId())
            .payableAmountFen(payableFen)
            .originalAmountFen(targetPriceFen)
            .creditedAmountFen(creditFen)
            .remainingValueCycles(remainingCycles.doubleValue())
            .effectiveExpireAt(effectiveNow.plusDays(targetDurationDays))
            .build();
    }

    public Map<String, Object> toMap(PlanChangeDecision decision) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("allowed", decision.isAllowed());
        item.put("action", decision.getAction());
        item.put("reason", decision.getReason());
        item.put("targetPlanId", decision.getTargetPlanId());
        item.put("currentPlanId", decision.getCurrentPlanId());
        item.put("payableAmountFen", decision.getPayableAmountFen());
        item.put("originalAmountFen", decision.getOriginalAmountFen());
        item.put("creditedAmountFen", decision.getCreditedAmountFen());
        item.put("remainingValueCycles", decision.getRemainingValueCycles());
        item.put("effectiveExpireAt", decision.getEffectiveExpireAt());
        return item;
    }

    private PlanChangeDecision purchase(VipPlan targetPlan, LocalDateTime now) {
        int targetDurationDays = Math.max(1, targetPlan.getDurationDays() == null ? 30 : targetPlan.getDurationDays());
        int targetPriceFen = Math.max(0, targetPlan.getPriceFen() == null ? 0 : targetPlan.getPriceFen());
        return PlanChangeDecision.builder()
            .allowed(true)
            .action("PURCHASE")
            .reason("当前无有效同类套餐，可直接购买")
            .targetPlanId(targetPlan.getId())
            .payableAmountFen(targetPriceFen)
            .originalAmountFen(targetPriceFen)
            .creditedAmountFen(0)
            .effectiveExpireAt(now.plusDays(targetDurationDays))
            .build();
    }

    private PlanChangeDecision blocked(String reason) {
        return PlanChangeDecision.builder()
            .allowed(false)
            .action("BLOCKED")
            .reason(reason)
            .payableAmountFen(0)
            .originalAmountFen(0)
            .creditedAmountFen(0)
            .build();
    }

    private boolean isSupportedPlan(VipPlan plan) {
        return "STANDARD".equalsIgnoreCase(safe(plan.getPlanCategory()))
            && "FIXED_TERM".equalsIgnoreCase(safe(plan.getQuotaGrantMode()))
            && "REPLACE_OR_EXTEND".equalsIgnoreCase(safe(plan.getStackingMode()));
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    @Data
    @Builder
    public static class PlanChangeDecision {
        private boolean allowed;
        private String action;
        private String reason;
        private Long targetPlanId;
        private Long currentPlanId;
        private Integer payableAmountFen;
        private Integer originalAmountFen;
        private Integer creditedAmountFen;
        private Double remainingValueCycles;
        private LocalDateTime effectiveExpireAt;
    }
}
