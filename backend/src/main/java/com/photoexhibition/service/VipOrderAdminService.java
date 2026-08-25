package com.photoexhibition.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.repository.UserPlanOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VipOrderAdminService {

    private final UserPlanOrderRepository userPlanOrderRepository;
    private final PaymentConfigService paymentConfigService;
    private final PaymentRefundService paymentRefundService;
    private final VipOrderLifecycleService vipOrderLifecycleService;
    private final ObjectMapper objectMapper;

    @Transactional
    public UserPlanOrder cancelOrder(Long orderId, String remark) {
        UserPlanOrder order = loadOrder(orderId);
        if ("REFUNDED".equalsIgnoreCase(order.getStatus())) {
            throw new RuntimeException("已退款订单不允许再取消");
        }
        if ("CANCELLED".equalsIgnoreCase(order.getStatus())) {
            return order;
        }
        PaymentProviderType providerType = resolveProviderType();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderNo", order.getOrderNo());
        payload.put("status", "CANCELLED");
        payload.put("source", "SUPER_ADMIN_MANUAL");
        if (remark != null && !remark.isBlank()) {
            payload.put("remark", remark.trim());
            order.setRemark(appendRemark(order.getRemark(), "取消原因: " + remark.trim()));
        }
        return vipOrderLifecycleService.markOrderCancelled(order, providerType, order.getExternalTradeNo(), payload, LocalDateTime.now());
    }

    @Transactional
    public UserPlanOrder refundOrder(Long orderId, Integer refundAmountFen, String remark) {
        UserPlanOrder order = loadOrder(orderId);
        if ("REFUNDED".equalsIgnoreCase(order.getStatus())) {
            return order;
        }
        if ("REQUESTED".equalsIgnoreCase(order.getRefundStatus())) {
            return order;
        }
        int effectiveRefund = refundAmountFen == null || refundAmountFen <= 0
            ? (order.getAmountFen() == null ? 0 : order.getAmountFen())
            : refundAmountFen;
        PaymentProviderType providerType = resolveProviderType();
        PaymentConfigService.PaymentResolvedSettings settings = paymentConfigService.getResolvedSettings();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderNo", order.getOrderNo());
        payload.put("refundAmountFen", effectiveRefund);
        payload.put("providerType", providerType.name());
        if (remark != null && !remark.isBlank()) {
            payload.put("remark", remark.trim());
            order.setRemark(appendRemark(order.getRemark(), "退款原因: " + remark.trim()));
        }
        if (settings.isMockEnabled()) {
            payload.put("status", "REFUNDED");
            payload.put("source", "SUPER_ADMIN_MANUAL_MOCK");
            return vipOrderLifecycleService.markOrderRefunded(order, providerType, order.getExternalTradeNo(), effectiveRefund, payload, LocalDateTime.now());
        }

        Map<String, Object> preview = paymentRefundService.previewByOrderId(orderId, effectiveRefund);
        if (!Boolean.TRUE.equals(preview.get("refundReady"))) {
            throw new RuntimeException(resolveRefundReadinessMessage(preview));
        }

        payload.put("status", "REQUESTED");
        payload.put("source", "SUPER_ADMIN_MANUAL_LIVE");
        payload.put("refundPreview", preview);
        order.setRefundStatus("REQUESTED");
        order.setRefundAmountFen(effectiveRefund);
        order.setGatewayStatus("REFUND_REQUESTED");
        order.setPaymentProviderType(providerType.name());
        order.setRefundedAt(null);
        order.setCallbackPayloadJson(toJson(payload));
        return userPlanOrderRepository.save(order);
    }

    @Transactional
    public UserPlanOrder confirmRefundSuccess(Long orderId, Integer refundAmountFen, String remark) {
        UserPlanOrder order = loadOrder(orderId);
        if ("REFUNDED".equalsIgnoreCase(order.getStatus())) {
            return order;
        }
        if (!"REQUESTED".equalsIgnoreCase(order.getRefundStatus())) {
            throw new RuntimeException("当前订单不处于退款处理中");
        }
        int effectiveRefund = refundAmountFen == null || refundAmountFen <= 0
            ? (order.getRefundAmountFen() == null || order.getRefundAmountFen() <= 0
                ? (order.getAmountFen() == null ? 0 : order.getAmountFen())
                : order.getRefundAmountFen())
            : refundAmountFen;
        PaymentProviderType providerType = resolveProviderType();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderNo", order.getOrderNo());
        payload.put("refundAmountFen", effectiveRefund);
        payload.put("providerType", providerType.name());
        payload.put("status", "REFUNDED");
        payload.put("source", "SUPER_ADMIN_MANUAL_CONFIRM_SUCCESS");
        if (remark != null && !remark.isBlank()) {
            payload.put("remark", remark.trim());
            order.setRemark(appendRemark(order.getRemark(), "人工确认退款成功: " + remark.trim()));
        }
        return vipOrderLifecycleService.markOrderRefunded(order, providerType, order.getExternalTradeNo(), effectiveRefund, payload, LocalDateTime.now());
    }

    @Transactional
    public UserPlanOrder markRefundFailed(Long orderId, String remark) {
        UserPlanOrder order = loadOrder(orderId);
        if ("REFUNDED".equalsIgnoreCase(order.getStatus())) {
            throw new RuntimeException("已退款订单不可标记退款失败");
        }
        if (!"REQUESTED".equalsIgnoreCase(order.getRefundStatus())) {
            throw new RuntimeException("当前订单不处于退款处理中");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderNo", order.getOrderNo());
        payload.put("status", "FAILED");
        payload.put("source", "SUPER_ADMIN_MANUAL_CONFIRM_FAILED");
        if (remark != null && !remark.isBlank()) {
            payload.put("remark", remark.trim());
            order.setRemark(appendRemark(order.getRemark(), "人工确认退款失败: " + remark.trim()));
        }
        order.setRefundStatus("FAILED");
        order.setGatewayStatus("REFUND_FAILED");
        order.setRefundedAt(null);
        order.setCallbackPayloadJson(toJson(payload));
        return userPlanOrderRepository.save(order);
    }

    private UserPlanOrder loadOrder(Long orderId) {
        return userPlanOrderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("VIP 订单不存在"));
    }

    private PaymentProviderType resolveProviderType() {
        PaymentConfigService.PaymentResolvedSettings settings = paymentConfigService.getResolvedSettings();
        return settings.getProviderType() == null ? PaymentProviderType.ALIPAY : settings.getProviderType();
    }

    private String appendRemark(String existing, String appended) {
        if (existing == null || existing.isBlank()) {
            return appended;
        }
        return existing + "\n" + appended;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{\"error\":\"serialize_failed\"}";
        }
    }

    private String resolveRefundReadinessMessage(Map<String, Object> preview) {
        Object supportMessage = preview == null ? null : preview.get("supportMessage");
        if (supportMessage != null && !String.valueOf(supportMessage).isBlank()) {
            return String.valueOf(supportMessage);
        }
        Object missingFields = preview == null ? null : preview.get("missingFields");
        if (missingFields instanceof Iterable) {
            StringBuilder builder = new StringBuilder();
            for (Object field : (Iterable<?>) missingFields) {
                if (field == null || String.valueOf(field).isBlank()) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append('、');
                }
                builder.append(field);
            }
            if (builder.length() > 0) {
                return "退款配置缺少字段：" + builder;
            }
        }
        return "当前退款链路尚未就绪";
    }
}
