package com.photoexhibition.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.entity.VipPlan;
import com.photoexhibition.repository.UserAccountRepository;
import com.photoexhibition.repository.UserPlanOrderRepository;
import com.photoexhibition.repository.VipPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class VipOrderLifecycleService {

    private final UserAccountRepository userAccountRepository;
    private final UserPlanOrderRepository userPlanOrderRepository;
    private final VipPlanRepository vipPlanRepository;
    private final ObjectMapper objectMapper;

    public String generateOrderNo() {
        return "VIP" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    @Transactional
    public UserPlanOrder markOrderPaid(UserPlanOrder order,
                                       PaymentProviderType providerType,
                                       String externalTradeNo,
                                       Map<String, Object> callbackPayload,
                                       LocalDateTime paidAt) {
        if (order == null) {
            throw new RuntimeException("VIP 订单不存在");
        }
        order.setStatus("PAID");
        order.setGatewayStatus("PAID");
        order.setPaymentProviderType(providerType == null ? null : providerType.name());
        if (externalTradeNo != null && !externalTradeNo.trim().isEmpty()) {
            order.setExternalTradeNo(externalTradeNo.trim());
        }
        if (paidAt != null) {
            order.setPaidAt(paidAt);
        } else if (order.getPaidAt() == null) {
            order.setPaidAt(LocalDateTime.now());
        }
        order.setPaymentNotifiedAt(LocalDateTime.now());
        if (callbackPayload != null && !callbackPayload.isEmpty()) {
            order.setCallbackPayloadJson(toJson(callbackPayload));
        }
        UserPlanOrder saved = userPlanOrderRepository.save(order);
        syncRenewalSourceOrderIfNeeded(saved);
        applyOrderToUserIfNeeded(saved);
        return saved;
    }

    @Transactional
    public UserPlanOrder markOrderCancelled(UserPlanOrder order,
                                            PaymentProviderType providerType,
                                            String externalTradeNo,
                                            Map<String, Object> callbackPayload,
                                            LocalDateTime cancelledAt) {
        if (order == null) {
            throw new RuntimeException("VIP 订单不存在");
        }
        order.setStatus("CANCELLED");
        order.setGatewayStatus("CANCELLED");
        order.setPaymentProviderType(providerType == null ? null : providerType.name());
        if (externalTradeNo != null && !externalTradeNo.trim().isEmpty()) {
            order.setExternalTradeNo(externalTradeNo.trim());
        }
        order.setCancelledAt(cancelledAt == null ? LocalDateTime.now() : cancelledAt);
        order.setPaymentNotifiedAt(LocalDateTime.now());
        if (callbackPayload != null && !callbackPayload.isEmpty()) {
            order.setCallbackPayloadJson(toJson(callbackPayload));
        }
        UserPlanOrder saved = userPlanOrderRepository.save(order);
        restoreRenewalSourceOrderIfNeeded(saved);
        return saved;
    }

    @Transactional
    public UserPlanOrder markOrderRefunded(UserPlanOrder order,
                                           PaymentProviderType providerType,
                                           String externalTradeNo,
                                           Integer refundAmountFen,
                                           Map<String, Object> callbackPayload,
                                           LocalDateTime refundedAt) {
        if (order == null) {
            throw new RuntimeException("VIP 订单不存在");
        }
        order.setStatus("REFUNDED");
        order.setGatewayStatus("REFUNDED");
        order.setRefundStatus("REFUNDED");
        order.setPaymentProviderType(providerType == null ? null : providerType.name());
        if (externalTradeNo != null && !externalTradeNo.trim().isEmpty()) {
            order.setExternalTradeNo(externalTradeNo.trim());
        }
        order.setRefundAmountFen(refundAmountFen == null ? order.getAmountFen() : Math.max(refundAmountFen, 0));
        order.setRefundedAt(refundedAt == null ? LocalDateTime.now() : refundedAt);
        order.setPaymentNotifiedAt(LocalDateTime.now());
        if (callbackPayload != null && !callbackPayload.isEmpty()) {
            order.setCallbackPayloadJson(toJson(callbackPayload));
        }
        return userPlanOrderRepository.save(order);
    }

    @Transactional
    public UserPlanOrder applyOrderToUserIfNeeded(UserPlanOrder order) {
        if (order == null || order.getUserId() == null || order.getVipPlanId() == null) {
            return order;
        }
        String status = order.getStatus() == null ? "" : order.getStatus().trim().toUpperCase();
        if (!"PAID".equals(status) && !"ACTIVE".equals(status)) {
            return order;
        }
        UserAccount user = userAccountRepository.findById(order.getUserId())
            .orElseThrow(() -> new RuntimeException("订单用户不存在"));
        VipPlan plan = vipPlanRepository.findById(order.getVipPlanId())
            .orElseThrow(() -> new RuntimeException("VIP 套餐不存在"));
        LocalDateTime effectivePaidAt = order.getPaidAt() == null ? LocalDateTime.now() : order.getPaidAt();
        LocalDateTime expireAt = order.getExpireAt();
        int durationDays = plan.getDurationDays() == null ? 30 : Math.max(1, plan.getDurationDays());
        String changeType = order.getChangeType() == null ? "" : order.getChangeType().trim().toUpperCase();
        if (expireAt == null) {
            if ("RENEWAL".equals(changeType) && user.getVipExpireAt() != null && user.getVipExpireAt().isAfter(effectivePaidAt)) {
                expireAt = user.getVipExpireAt().plusDays(durationDays);
            } else {
                expireAt = effectivePaidAt.plusDays(durationDays);
            }
            order.setExpireAt(expireAt);
        }
        if (Boolean.TRUE.equals(order.getAutoRenewEnabled()) && order.getNextRenewalAt() == null) {
            order.setNextRenewalAt(expireAt);
        }
        order = userPlanOrderRepository.save(order);
        user.setCurrentVipPlanId(plan.getId());
        user.setVipExpireAt(expireAt);
        userAccountRepository.save(user);
        return order;
    }

    private void syncRenewalSourceOrderIfNeeded(UserPlanOrder renewalOrder) {
        if (renewalOrder == null || renewalOrder.getRenewalSourceOrderId() == null) {
            return;
        }
        userPlanOrderRepository.findById(renewalOrder.getRenewalSourceOrderId()).ifPresent(sourceOrder -> {
            sourceOrder.setGatewayStatus("RENEWAL_COMPLETED");
            sourceOrder.setRemark(appendRemark(
                sourceOrder.getRemark(),
                "续费子单已支付: " + renewalOrder.getOrderNo()
            ));
            userPlanOrderRepository.save(sourceOrder);
        });
    }

    private void restoreRenewalSourceOrderIfNeeded(UserPlanOrder renewalOrder) {
        if (renewalOrder == null || renewalOrder.getRenewalSourceOrderId() == null) {
            return;
        }
        userPlanOrderRepository.findById(renewalOrder.getRenewalSourceOrderId()).ifPresent(sourceOrder -> {
            String sourceStatus = sourceOrder.getStatus() == null ? "" : sourceOrder.getStatus().trim().toUpperCase();
            if (!"PAID".equals(sourceStatus) && !"ACTIVE".equals(sourceStatus)) {
                return;
            }
            sourceOrder.setGatewayStatus("RENEWAL_CANCELLED");
            if (!Boolean.TRUE.equals(sourceOrder.getAutoRenewEnabled())) {
                sourceOrder.setAutoRenewEnabled(true);
            }
            if (sourceOrder.getNextRenewalAt() == null && sourceOrder.getExpireAt() != null) {
                sourceOrder.setNextRenewalAt(sourceOrder.getExpireAt());
            }
            sourceOrder.setRemark(appendRemark(
                sourceOrder.getRemark(),
                "续费子单已取消: " + renewalOrder.getOrderNo()
            ));
            userPlanOrderRepository.save(sourceOrder);
        });
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

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"serialize_failed\"}";
        }
    }
}
