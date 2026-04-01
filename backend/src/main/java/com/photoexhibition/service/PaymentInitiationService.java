package com.photoexhibition.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.entity.VipPlan;
import com.photoexhibition.repository.UserAccountRepository;
import com.photoexhibition.repository.UserPlanOrderRepository;
import com.photoexhibition.repository.VipPlanRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PaymentInitiationService {

    private final PaymentConfigService paymentConfigService;
    private final PaymentGatewayService paymentGatewayService;
    private final UserPlanOrderRepository userPlanOrderRepository;
    private final UserAccountRepository userAccountRepository;
    private final VipPlanRepository vipPlanRepository;
    private final List<PaymentProviderAdapter> paymentProviderAdapters;
    private final ObjectMapper objectMapper;

    @Transactional
    public Map<String, Object> initiateByOrderId(Long orderId) {
        UserPlanOrder order = userPlanOrderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("VIP 订单不存在"));
        return initiateInternal(order, null);
    }

    @Transactional
    public Map<String, Object> initiateForUser(Long orderId, Long userId) {
        UserPlanOrder order = userPlanOrderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("VIP 订单不存在"));
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new RuntimeException("无权发起该订单支付");
        }
        return initiateInternal(order, userId);
    }

    private Map<String, Object> initiateInternal(UserPlanOrder order, Long expectedUserId) {
        validateOrderCanInitiate(order);
        UserAccount user = userAccountRepository.findById(order.getUserId())
            .orElseThrow(() -> new RuntimeException("订单用户不存在"));
        if (expectedUserId != null && !Objects.equals(user.getId(), expectedUserId)) {
            throw new RuntimeException("订单用户不匹配");
        }
        VipPlan plan = vipPlanRepository.findById(order.getVipPlanId())
            .orElseThrow(() -> new RuntimeException("VIP 套餐不存在"));
        PaymentConfigService.PaymentResolvedSettings settings = paymentConfigService.getResolvedSettings();
        if (!settings.isEnabled()) {
            throw new RuntimeException("支付功能未启用");
        }

        PaymentGatewayService.PaymentPreview preview = paymentGatewayService.preview(order, plan, user);
        validatePreviewCanInitiate(preview);
        PaymentProviderType providerType = settings.getProviderType() == null ? PaymentProviderType.ALIPAY : settings.getProviderType();
        PaymentProviderAdapter adapter = paymentProviderAdapters.stream()
            .filter(item -> item.supports(providerType))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("未找到支付平台适配器: " + providerType));

        PaymentInitiationResult initiationResult = adapter.initiate(order, plan, user, settings, preview);
        persistInitiationState(order, providerType, initiationResult, preview);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("orderId", order.getId());
        resp.put("orderNo", order.getOrderNo());
        resp.put("providerType", initiationResult.getProviderType());
        resp.put("providerLabel", initiationResult.getProviderLabel());
        resp.put("httpMethod", initiationResult.getHttpMethod());
        resp.put("launchUrl", initiationResult.getLaunchUrl());
        resp.put("redirect", initiationResult.isRedirect());
        resp.put("actionType", initiationResult.getActionType());
        resp.put("mockMode", initiationResult.isMockMode());
        resp.put("liveModeReady", initiationResult.isLiveModeReady());
        resp.put("message", initiationResult.getMessage());
        resp.put("headers", initiationResult.getHeaders());
        resp.put("formFields", initiationResult.getFormFields());
        resp.put("qrCodeText", initiationResult.getQrCodeText());
        resp.put("payload", initiationResult.getPayload());
        resp.put("preview", preview);
        return resp;
    }

    private void persistInitiationState(UserPlanOrder order,
                                        PaymentProviderType providerType,
                                        PaymentInitiationResult initiationResult,
                                        PaymentGatewayService.PaymentPreview preview) {
        if (order == null) {
            return;
        }
        Map<String, Object> initiationSnapshot = new LinkedHashMap<>();
        initiationSnapshot.put("kind", "payment_initiation");
        initiationSnapshot.put("providerType", providerType == null ? null : providerType.name());
        initiationSnapshot.put("providerLabel", initiationResult == null ? null : initiationResult.getProviderLabel());
        initiationSnapshot.put("actionType", initiationResult == null ? null : initiationResult.getActionType());
        initiationSnapshot.put("httpMethod", initiationResult == null ? null : initiationResult.getHttpMethod());
        initiationSnapshot.put("launchUrl", initiationResult == null ? null : initiationResult.getLaunchUrl());
        initiationSnapshot.put("redirect", initiationResult != null && initiationResult.isRedirect());
        initiationSnapshot.put("mockMode", initiationResult != null && initiationResult.isMockMode());
        initiationSnapshot.put("liveModeReady", initiationResult != null && initiationResult.isLiveModeReady());
        initiationSnapshot.put("headers", initiationResult == null ? null : initiationResult.getHeaders());
        initiationSnapshot.put("formFields", initiationResult == null ? null : initiationResult.getFormFields());
        initiationSnapshot.put("payload", initiationResult == null ? null : initiationResult.getPayload());
        initiationSnapshot.put("qrCodeText", initiationResult == null ? null : initiationResult.getQrCodeText());
        order.setPaymentProviderType(providerType == null ? null : providerType.name());
        order.setGatewayStatus(preview != null && preview.isMockEnabled() ? "PAYMENT_INITIATED_MOCK" : "PAYMENT_INITIATED");
        order.setCallbackPayloadJson(toJson(initiationSnapshot));
        userPlanOrderRepository.save(order);
    }

    private void validatePreviewCanInitiate(PaymentGatewayService.PaymentPreview preview) {
        if (preview == null) {
            throw new RuntimeException("支付预览生成失败");
        }
        if (!preview.isEnabled()) {
            throw new RuntimeException("支付功能未启用");
        }
        if (preview.isMockEnabled()) {
            return;
        }
        if (!preview.isLiveModeReady()) {
            String missing = preview.getMissingFields() == null || preview.getMissingFields().isEmpty()
                ? "支付配置不完整"
                : "支付配置缺少字段：" + String.join("、", preview.getMissingFields());
            throw new RuntimeException(missing);
        }
    }

    private void validateOrderCanInitiate(UserPlanOrder order) {
        String status = order.getStatus() == null ? "" : order.getStatus().trim().toUpperCase();
        if (!"CREATED".equals(status)) {
            throw new RuntimeException("当前订单状态不可发起支付");
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{\"error\":\"serialize_failed\"}";
        }
    }

    @Data
    @Builder
    public static class PaymentInitiationResult {
        private String providerType;
        private String providerLabel;
        private String orderNo;
        private String httpMethod;
        private String launchUrl;
        private boolean redirect;
        private String actionType;
        private boolean mockMode;
        private boolean liveModeReady;
        private String message;
        private Map<String, Object> headers;
        private Map<String, Object> formFields;
        private String qrCodeText;
        private Map<String, Object> payload;
    }
}
