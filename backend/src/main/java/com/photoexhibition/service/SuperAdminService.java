package com.photoexhibition.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.entity.StorageProvider;
import com.photoexhibition.entity.StorageType;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserRole;
import com.photoexhibition.entity.UserStatus;
import com.photoexhibition.entity.LoginRecord;
import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.entity.VipPlan;
import com.photoexhibition.repository.LoginRecordRepository;
import com.photoexhibition.repository.OperationLogRepository;
import com.photoexhibition.repository.StorageProviderRepository;
import com.photoexhibition.repository.UserAccountRepository;
import com.photoexhibition.repository.UserPlanOrderRepository;
import com.photoexhibition.repository.VipPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SuperAdminService {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final UserAccountRepository userAccountRepository;
    private final LoginRecordRepository loginRecordRepository;
    private final OperationLogRepository operationLogRepository;
    private final StorageProviderRepository storageProviderRepository;
    private final VipPlanRepository vipPlanRepository;
    private final UserPlanOrderRepository userPlanOrderRepository;
    private final SystemConfigService systemConfigService;
    private final SmsConfigService smsConfigService;
    private final EmailConfigService emailConfigService;
    private final PaymentConfigService paymentConfigService;
    private final PaymentGatewayService paymentGatewayService;
    private final PaymentInitiationService paymentInitiationService;
    private final PaymentRefundService paymentRefundService;
    private final PaymentCallbackService paymentCallbackService;
    private final VipOrderLifecycleService vipOrderLifecycleService;
    private final VipOrderAdminService vipOrderAdminService;
    private final VipRenewalService vipRenewalService;
    private final EmailSenderService emailSenderService;
    private final SmsVerificationService smsVerificationService;
    private final LegacyDataMigrationService legacyDataMigrationService;
    private final StorageProviderService storageProviderService;
    private final UserStorageService userStorageService;
    private final UserPathService userPathService;
    private final ModelManagementService modelManagementService;
    private final ScanTaskService scanTaskService;
    private final PhotoScanService photoScanService;
    private final BackgroundRemovalService backgroundRemovalService;
    private final RequestMonitoringService requestMonitoringService;
    private final StorageMigrationService storageMigrationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public Map<String, Object> getOverview() {
        List<UserAccountRepository.UserOverviewProjection> users = userAccountRepository.findAllOverviewSnapshots();
        List<StorageProvider> storageProviders = storageProviderRepository.findAllByOrderByPriorityAscIdAsc();
        Map<Long, Long> enabledPlanQuotaById = vipPlanRepository.findAllByOrderBySortOrderAscIdAsc().stream()
            .filter(plan -> Boolean.TRUE.equals(plan.getEnabled()))
            .collect(Collectors.toMap(VipPlan::getId, plan -> defaultLong(plan.getExtraQuotaBytes()), (left, right) -> right));

        long totalQuotaBytes = users.stream()
            .mapToLong(user -> resolveEffectiveQuotaBytes(user, enabledPlanQuotaById))
            .sum();
        long totalUsedBytes = users.stream()
            .map(UserAccountRepository.UserOverviewProjection::getStorageUsedBytes)
            .mapToLong(this::defaultLong)
            .sum();

        Map<String, Object> resp = new LinkedHashMap<>();
        SmsConfigService.SmsResolvedSettings smsSettings = smsConfigService.getResolvedSettings();
        EmailConfigService.EmailResolvedSettings emailSettings = emailConfigService.getResolvedSettings();
        PaymentConfigService.PaymentResolvedSettings paymentSettings = paymentConfigService.getResolvedSettings();
        resp.put("userCount", userAccountRepository.count());
        resp.put("activeUserCount", userAccountRepository.countByStatus(UserStatus.ACTIVE));
        resp.put("disabledUserCount", userAccountRepository.countByStatus(UserStatus.DISABLED));
        resp.put("lockedUserCount", userAccountRepository.countByStatus(UserStatus.LOCKED));
        resp.put("storageProviderCount", storageProviders.size());
        resp.put("enabledStorageProviderCount", storageProviders.stream().filter(provider -> Boolean.TRUE.equals(provider.getEnabled())).count());
        resp.put("multiUserEnabled", systemConfigService.isMultiUserEnabled());
        resp.put("scanSchedulerEnabled", systemConfigService.isScanSchedulerEnabled());
        resp.put("scanWorkerCount", systemConfigService.getScanWorkerCount());
        resp.put("forceBindPhone", systemConfigService.isForceBindPhone());
        resp.put("autoRenewSchedulerEnabled", systemConfigService.isAutoRenewSchedulerEnabled());
        resp.put("smsProviderType", smsSettings.getProviderType() != null ? smsSettings.getProviderType().name() : SmsProviderType.ALIYUN.name());
        resp.put("smsEnabled", smsSettings.isEnabled());
        resp.put("smsMockEnabled", smsSettings.isMockEnabled());
        resp.put("emailProviderType", emailSettings.getProviderType() != null ? emailSettings.getProviderType().name() : EmailProviderType.SMTP.name());
        resp.put("emailEnabled", emailSettings.isEnabled());
        resp.put("emailMockEnabled", emailSettings.isMockEnabled());
        resp.put("emailCodeLoginEnabled", emailSettings.isCodeLoginEnabled());
        resp.put("emailCodeExpireMinutes", emailSettings.getCodeExpireMinutes());
        resp.put("paymentProviderType", paymentSettings.getProviderType() != null ? paymentSettings.getProviderType().name() : PaymentProviderType.ALIPAY.name());
        resp.put("paymentEnabled", paymentSettings.isEnabled());
        resp.put("paymentMockEnabled", paymentSettings.isMockEnabled());
        resp.put("defaultUserQuotaBytes", systemConfigService.getDefaultUserQuotaBytes());
        resp.put("defaultVipExtraQuotaBytes", systemConfigService.getDefaultVipExtraQuotaBytes());
        resp.put("localStorageRoot", systemConfigService.getLocalStorageRoot());
        resp.put("userDataRoot", systemConfigService.getUserDataRoot());
        resp.put("totalQuotaBytes", totalQuotaBytes);
        resp.put("totalUsedBytes", totalUsedBytes);
        Map<String, Object> modelSummary = modelManagementService.getModelSummary();
        resp.put("modelCount", modelSummary.get("modelCount"));
        resp.put("missingModelFileCount", modelSummary.get("missingFileCount"));
        resp.put("inactiveModelCount", modelSummary.get("inactiveModelCount"));
        resp.put("modelHealthy", modelSummary.get("healthy"));
        return resp;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getProcessingOverview() {
        Map<String, Object> resp = new LinkedHashMap<>();
        List<Map<String, Object>> workers = new ArrayList<>();
        workers.add(requestMonitoringService.getOverview());
        workers.add(scanTaskService.getSuperAdminQueueOverview());
        workers.add(photoScanService.getAsyncTaskOverview());
        workers.add(backgroundRemovalService.getProcessingOverview());
        workers.add(modelManagementService.getTaskOverview());

        long activeWorkerGroups = workers.stream()
            .filter(item -> Boolean.TRUE.equals(item.get("queueActive"))
                || Boolean.TRUE.equals(item.get("running"))
                || asLong(item.get("runningTaskCount")) > 0
                || asLong(item.get("activeThreads")) > 0
                || asLong(item.get("activeWorkers")) > 0)
            .count();

        resp.put("workerCount", workers.size());
        resp.put("activeWorkerGroupCount", activeWorkerGroups);
        resp.put("workers", workers);
        resp.put("nonBlockingNote", "扫描队列、背景移除、模型重建均在独立后台线程中执行，接口请求仅负责提交任务与查询状态。");
        return resp;
    }

    @Transactional
    public Map<String, Object> getSettings() {
        Map<String, Object> resp = new LinkedHashMap<>();
        SmsConfigService.SmsResolvedSettings smsSettings = smsConfigService.getResolvedSettings();
        EmailConfigService.EmailResolvedSettings emailSettings = emailConfigService.getResolvedSettings();
        PaymentConfigService.PaymentResolvedSettings paymentSettings = paymentConfigService.getResolvedSettings();
        resp.put("multiUserEnabled", systemConfigService.isMultiUserEnabled());
        resp.put("scanSchedulerEnabled", systemConfigService.isScanSchedulerEnabled());
        resp.put("scanWorkerCount", systemConfigService.getScanWorkerCount());
        resp.put("forceBindPhone", systemConfigService.isForceBindPhone());
        resp.put("autoRenewSchedulerEnabled", systemConfigService.isAutoRenewSchedulerEnabled());
        resp.put("smsProviderType", smsSettings.getProviderType() != null ? smsSettings.getProviderType().name() : SmsProviderType.ALIYUN.name());
        resp.put("smsEnabled", smsSettings.isEnabled());
        resp.put("smsMockEnabled", smsSettings.isMockEnabled());
        resp.put("smsEndpoint", smsSettings.getEndpoint());
        resp.put("smsRegionId", smsSettings.getRegionId());
        resp.put("smsAccessKeyId", smsSettings.getAccessKeyId());
        resp.put("smsAccessKeySecret", smsSettings.getAccessKeySecret());
        resp.put("smsSignName", smsSettings.getSignName());
        resp.put("smsTemplateCode", smsSettings.getTemplateCode());
        resp.put("smsTemplateParamName", smsSettings.getTemplateParamName());
        resp.put("smsSdkAppId", smsSettings.getSdkAppId());
        resp.put("smsCodeExpireMinutes", smsSettings.getCodeExpireMinutes());
        resp.put("emailProviderType", emailSettings.getProviderType() != null ? emailSettings.getProviderType().name() : EmailProviderType.SMTP.name());
        resp.put("emailEnabled", emailSettings.isEnabled());
        resp.put("emailMockEnabled", emailSettings.isMockEnabled());
        resp.put("emailCodeLoginEnabled", emailSettings.isCodeLoginEnabled());
        resp.put("emailCodeExpireMinutes", emailSettings.getCodeExpireMinutes());
        resp.put("emailHost", emailSettings.getHost());
        resp.put("emailPort", emailSettings.getPort());
        resp.put("emailUsername", emailSettings.getUsername());
        resp.put("emailPassword", emailSettings.getPassword());
        resp.put("emailProtocol", emailSettings.getProtocol());
        resp.put("emailFromAddress", emailSettings.getFromAddress());
        resp.put("emailFromName", emailSettings.getFromName());
        resp.put("emailReplyTo", emailSettings.getReplyTo());
        resp.put("emailSslEnabled", emailSettings.isSslEnabled());
        resp.put("emailStarttlsEnabled", emailSettings.isStarttlsEnabled());
        resp.put("emailTestRecipient", emailSettings.getTestRecipient());
        resp.put("paymentProviderType", paymentSettings.getProviderType() != null ? paymentSettings.getProviderType().name() : PaymentProviderType.ALIPAY.name());
        resp.put("paymentEnabled", paymentSettings.isEnabled());
        resp.put("paymentMockEnabled", paymentSettings.isMockEnabled());
        resp.put("paymentAppId", paymentSettings.getAppId());
        resp.put("paymentMerchantId", paymentSettings.getMerchantId());
        resp.put("paymentMerchantName", paymentSettings.getMerchantName());
        resp.put("paymentPrivateKey", paymentSettings.getPrivateKey());
        resp.put("paymentPublicKey", paymentSettings.getPublicKey());
        resp.put("paymentApiBaseUrl", paymentSettings.getApiBaseUrl());
        resp.put("paymentNotifyUrl", paymentSettings.getNotifyUrl());
        resp.put("paymentReturnUrl", paymentSettings.getReturnUrl());
        resp.put("paymentWebhookSecret", paymentSettings.getWebhookSecret());
        resp.put("paymentCurrency", paymentSettings.getCurrency());
        resp.put("paymentVerificationMode", paymentSettings.getVerificationMode());
        resp.put("paymentApiSecret", paymentSettings.getApiSecret());
        resp.put("paymentCertificateSerialNo", paymentSettings.getCertificateSerialNo());
        resp.put("paymentPlatformCertificate", paymentSettings.getPlatformCertificate());
        resp.put("defaultUserQuotaBytes", systemConfigService.getDefaultUserQuotaBytes());
        resp.put("defaultVipExtraQuotaBytes", systemConfigService.getDefaultVipExtraQuotaBytes());
        resp.put("localStorageRoot", systemConfigService.getLocalStorageRoot());
        resp.put("userDataRoot", systemConfigService.getUserDataRoot());
        resp.put("defaultStorageProviderId", storageProviderRepository.findFirstByIsDefaultTrue()
            .map(StorageProvider::getId)
            .orElse(null));
        return resp;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTablePreferences() {
        try {
            String raw = systemConfigService.getSuperAdminTablePreferences();
            Map<String, Object> preferences = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
            return preferences == null ? new LinkedHashMap<>() : new LinkedHashMap<>(preferences);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    @Transactional
    public Map<String, Object> updateTablePreferences(Map<String, Object> request) {
        Map<String, Object> sanitized = request == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request);
        try {
            systemConfigService.setSuperAdminTablePreferences(objectMapper.writeValueAsString(sanitized));
        } catch (Exception e) {
            throw new RuntimeException("保存表格偏好失败");
        }
        return getTablePreferences();
    }

    @Transactional
    public Map<String, Object> updateSettings(Map<String, Object> request) {
        if (request.containsKey("multiUserEnabled")) {
            systemConfigService.setMultiUserEnabled(parseBoolean(request.get("multiUserEnabled"), "multiUserEnabled"));
        }
        if (request.containsKey("scanSchedulerEnabled")) {
            systemConfigService.setScanSchedulerEnabled(parseBoolean(request.get("scanSchedulerEnabled"), "scanSchedulerEnabled"));
        }
        if (request.containsKey("scanWorkerCount")) {
            systemConfigService.setScanWorkerCount(parseInteger(request.get("scanWorkerCount"), "scanWorkerCount"));
        }
        if (request.containsKey("forceBindPhone")) {
            systemConfigService.setForceBindPhone(parseBoolean(request.get("forceBindPhone"), "forceBindPhone"));
        }
        if (request.containsKey("autoRenewSchedulerEnabled")) {
            systemConfigService.setAutoRenewSchedulerEnabled(parseBoolean(request.get("autoRenewSchedulerEnabled"), "autoRenewSchedulerEnabled"));
        }
        if (request.containsKey("smsProviderType")) {
            smsConfigService.setProviderType(parseRequiredString(request.get("smsProviderType"), "smsProviderType"));
        }
        if (request.containsKey("smsEnabled")) {
            smsConfigService.setEnabled(parseBoolean(request.get("smsEnabled"), "smsEnabled"));
        }
        if (request.containsKey("smsMockEnabled")) {
            smsConfigService.setMockEnabled(parseBoolean(request.get("smsMockEnabled"), "smsMockEnabled"));
        }
        if (request.containsKey("smsEndpoint")) {
            smsConfigService.setEndpoint(parseNullableString(request.get("smsEndpoint")));
        }
        if (request.containsKey("smsRegionId")) {
            smsConfigService.setRegionId(parseNullableString(request.get("smsRegionId")));
        }
        if (request.containsKey("smsAccessKeyId")) {
            smsConfigService.setAccessKeyId(parseNullableString(request.get("smsAccessKeyId")));
        }
        if (request.containsKey("smsAccessKeySecret")) {
            smsConfigService.setAccessKeySecret(parseNullableString(request.get("smsAccessKeySecret")));
        }
        if (request.containsKey("smsSignName")) {
            smsConfigService.setSignName(parseNullableString(request.get("smsSignName")));
        }
        if (request.containsKey("smsTemplateCode")) {
            smsConfigService.setTemplateCode(parseNullableString(request.get("smsTemplateCode")));
        }
        if (request.containsKey("smsTemplateParamName")) {
            smsConfigService.setTemplateParamName(parseNullableString(request.get("smsTemplateParamName")));
        }
        if (request.containsKey("smsSdkAppId")) {
            smsConfigService.setSdkAppId(parseNullableString(request.get("smsSdkAppId")));
        }
        if (request.containsKey("smsCodeExpireMinutes")) {
            smsConfigService.setCodeExpireMinutes(parseInteger(request.get("smsCodeExpireMinutes"), "smsCodeExpireMinutes"));
        }
        if (request.containsKey("emailProviderType")) {
            emailConfigService.setProviderType(parseRequiredString(request.get("emailProviderType"), "emailProviderType"));
        }
        if (request.containsKey("emailEnabled")) {
            emailConfigService.setEnabled(parseBoolean(request.get("emailEnabled"), "emailEnabled"));
        }
        if (request.containsKey("emailMockEnabled")) {
            emailConfigService.setMockEnabled(parseBoolean(request.get("emailMockEnabled"), "emailMockEnabled"));
        }
        if (request.containsKey("emailCodeLoginEnabled")) {
            emailConfigService.setCodeLoginEnabled(parseBoolean(request.get("emailCodeLoginEnabled"), "emailCodeLoginEnabled"));
        }
        if (request.containsKey("emailCodeExpireMinutes")) {
            emailConfigService.setCodeExpireMinutes(parseInteger(request.get("emailCodeExpireMinutes"), "emailCodeExpireMinutes"));
        }
        if (request.containsKey("emailHost")) {
            emailConfigService.setHost(parseNullableString(request.get("emailHost")));
        }
        if (request.containsKey("emailPort")) {
            emailConfigService.setPort(parseInteger(request.get("emailPort"), "emailPort"));
        }
        if (request.containsKey("emailUsername")) {
            emailConfigService.setUsername(parseNullableString(request.get("emailUsername")));
        }
        if (request.containsKey("emailPassword")) {
            emailConfigService.setPassword(parseNullableString(request.get("emailPassword")));
        }
        if (request.containsKey("emailProtocol")) {
            emailConfigService.setProtocol(parseNullableString(request.get("emailProtocol")));
        }
        if (request.containsKey("emailFromAddress")) {
            emailConfigService.setFromAddress(parseNullableString(request.get("emailFromAddress")));
        }
        if (request.containsKey("emailFromName")) {
            emailConfigService.setFromName(parseNullableString(request.get("emailFromName")));
        }
        if (request.containsKey("emailReplyTo")) {
            emailConfigService.setReplyTo(parseNullableString(request.get("emailReplyTo")));
        }
        if (request.containsKey("emailSslEnabled")) {
            emailConfigService.setSslEnabled(parseBoolean(request.get("emailSslEnabled"), "emailSslEnabled"));
        }
        if (request.containsKey("emailStarttlsEnabled")) {
            emailConfigService.setStarttlsEnabled(parseBoolean(request.get("emailStarttlsEnabled"), "emailStarttlsEnabled"));
        }
        if (request.containsKey("emailTestRecipient")) {
            emailConfigService.setTestRecipient(parseNullableString(request.get("emailTestRecipient")));
        }
        if (request.containsKey("paymentProviderType")) {
            paymentConfigService.setProviderType(parseRequiredString(request.get("paymentProviderType"), "paymentProviderType"));
        }
        if (request.containsKey("paymentEnabled")) {
            paymentConfigService.setEnabled(parseBoolean(request.get("paymentEnabled"), "paymentEnabled"));
        }
        if (request.containsKey("paymentMockEnabled")) {
            paymentConfigService.setMockEnabled(parseBoolean(request.get("paymentMockEnabled"), "paymentMockEnabled"));
        }
        if (request.containsKey("paymentAppId")) {
            paymentConfigService.setAppId(parseNullableString(request.get("paymentAppId")));
        }
        if (request.containsKey("paymentMerchantId")) {
            paymentConfigService.setMerchantId(parseNullableString(request.get("paymentMerchantId")));
        }
        if (request.containsKey("paymentMerchantName")) {
            paymentConfigService.setMerchantName(parseNullableString(request.get("paymentMerchantName")));
        }
        if (request.containsKey("paymentPrivateKey")) {
            paymentConfigService.setPrivateKey(parseNullableString(request.get("paymentPrivateKey")));
        }
        if (request.containsKey("paymentPublicKey")) {
            paymentConfigService.setPublicKey(parseNullableString(request.get("paymentPublicKey")));
        }
        if (request.containsKey("paymentApiBaseUrl")) {
            paymentConfigService.setApiBaseUrl(parseNullableString(request.get("paymentApiBaseUrl")));
        }
        if (request.containsKey("paymentNotifyUrl")) {
            paymentConfigService.setNotifyUrl(parseNullableString(request.get("paymentNotifyUrl")));
        }
        if (request.containsKey("paymentReturnUrl")) {
            paymentConfigService.setReturnUrl(parseNullableString(request.get("paymentReturnUrl")));
        }
        if (request.containsKey("paymentWebhookSecret")) {
            paymentConfigService.setWebhookSecret(parseNullableString(request.get("paymentWebhookSecret")));
        }
        if (request.containsKey("paymentCurrency")) {
            paymentConfigService.setCurrency(parseNullableString(request.get("paymentCurrency")));
        }
        if (request.containsKey("paymentVerificationMode")) {
            paymentConfigService.setVerificationMode(parseNullableString(request.get("paymentVerificationMode")));
        }
        if (request.containsKey("paymentApiSecret")) {
            paymentConfigService.setApiSecret(parseNullableString(request.get("paymentApiSecret")));
        }
        if (request.containsKey("paymentCertificateSerialNo")) {
            paymentConfigService.setCertificateSerialNo(parseNullableString(request.get("paymentCertificateSerialNo")));
        }
        if (request.containsKey("paymentPlatformCertificate")) {
            paymentConfigService.setPlatformCertificate(parseNullableString(request.get("paymentPlatformCertificate")));
        }
        if (request.containsKey("defaultUserQuotaBytes")) {
            systemConfigService.setDefaultUserQuotaBytes(parseLong(request.get("defaultUserQuotaBytes"), "defaultUserQuotaBytes"));
        }
        if (request.containsKey("defaultVipExtraQuotaBytes")) {
            systemConfigService.setDefaultVipExtraQuotaBytes(parseLong(request.get("defaultVipExtraQuotaBytes"), "defaultVipExtraQuotaBytes"));
        }
        if (request.containsKey("localStorageRoot")) {
            systemConfigService.setLocalStorageRoot(parseRequiredString(request.get("localStorageRoot"), "localStorageRoot"));
        }
        if (request.containsKey("userDataRoot")) {
            systemConfigService.setUserDataRoot(parseRequiredString(request.get("userDataRoot"), "userDataRoot"));
        }

        StorageProvider defaultProvider = storageProviderService.ensureDefaultLocalStorageProvider();
        if (defaultProvider.getType() == StorageType.LOCAL) {
            defaultProvider.setBaseDirectory(systemConfigService.getLocalStorageRoot());
            storageProviderRepository.save(defaultProvider);
        }

        if (request.containsKey("defaultStorageProviderId")) {
            Long providerId = parseLongObject(request.get("defaultStorageProviderId"), "defaultStorageProviderId");
            if (providerId != null) {
                storageProviderService.resolveUploadProvider(null, providerId);
                setDefaultStorageProvider(providerId);
            }
        }

        return getSettings();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> sendTestEmail(String recipient) {
        EmailSenderService.EmailSendResult result = emailSenderService.sendTestEmail(recipient);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", result.isSuccess());
        resp.put("providerType", result.getProviderType());
        resp.put("recipient", result.getRecipient());
        resp.put("message", result.getMessage());
        return resp;
    }

    @Transactional
    public Map<String, Object> sendTestSmsCode(String phone, String requestIp) {
        Map<String, Object> resp = new LinkedHashMap<>(smsVerificationService.sendTestCode(phone, requestIp));
        SmsConfigService.SmsResolvedSettings smsSettings = smsConfigService.getResolvedSettings();
        resp.put("providerType", smsSettings.getProviderType() != null ? smsSettings.getProviderType().name() : SmsProviderType.ALIYUN.name());
        return resp;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> sendCustomEmail(String recipient, String subject, String content, Boolean html) {
        EmailSenderService.EmailSendResult result = emailSenderService.sendEmail(recipient, subject, content, Boolean.TRUE.equals(html));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", result.isSuccess());
        resp.put("providerType", result.getProviderType());
        resp.put("recipient", result.getRecipient());
        resp.put("message", result.getMessage());
        return resp;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listEmailTemplates() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("templates", emailSenderService.listTemplates());
        return resp;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> previewEmailTemplate(String templateKey, Map<String, Object> variables) {
        return emailSenderService.previewTemplate(templateKey, variables);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> sendTemplateEmail(String recipient, String templateKey, Map<String, Object> variables) {
        Map<String, Object> preview = emailSenderService.previewTemplate(templateKey, variables);
        EmailSenderService.EmailSendResult result = emailSenderService.sendTemplateEmail(recipient, templateKey, variables);
        Map<String, Object> resp = new LinkedHashMap<>(preview);
        resp.put("success", result.isSuccess());
        resp.put("providerType", result.getProviderType());
        resp.put("recipient", result.getRecipient());
        resp.put("message", result.getMessage());
        return resp;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> previewVipOrderPayment(Long orderId) {
        UserPlanOrder order = userPlanOrderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("VIP 订单不存在"));
        UserAccount user = userAccountRepository.findById(order.getUserId())
            .orElseThrow(() -> new RuntimeException("订单用户不存在"));
        VipPlan plan = vipPlanRepository.findById(order.getVipPlanId())
            .orElseThrow(() -> new RuntimeException("VIP 套餐不存在"));
        PaymentGatewayService.PaymentPreview preview = paymentGatewayService.preview(order, plan, user);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("orderId", order.getId());
        resp.put("orderNo", order.getOrderNo());
        resp.put("userId", user.getId());
        resp.put("username", user.getUsername());
        resp.put("vipPlanId", plan.getId());
        resp.put("vipPlanName", plan.getName());
        resp.put("providerType", preview.getProviderType());
        resp.put("providerLabel", preview.getProviderLabel());
        resp.put("enabled", preview.isEnabled());
        resp.put("mockEnabled", preview.isMockEnabled());
        resp.put("liveModeReady", preview.isLiveModeReady());
        resp.put("currency", preview.getCurrency());
        resp.put("apiBaseUrl", preview.getApiBaseUrl());
        resp.put("missingFields", preview.getMissingFields());
        resp.put("supportMessage", preview.getSupportMessage());
        resp.put("verificationMode", preview.getVerificationMode());
        resp.put("initiationMode", preview.getInitiationMode());
        resp.put("refundMode", preview.getRefundMode());
        resp.put("capabilityTags", preview.getCapabilityTags());
        resp.put("integrationSteps", preview.getIntegrationSteps());
        resp.put("requestPayload", preview.getRequestPayload());
        resp.put("callbackPayload", preview.getCallbackPayload());
        return resp;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> initiateVipOrderPayment(Long orderId) {
        return paymentInitiationService.initiateByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> previewVipOrderRefund(Long orderId, Integer refundAmountFen) {
        return paymentRefundService.previewByOrderId(orderId, refundAmountFen);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> previewPaymentNotify(String providerType, Map<String, Object> payload, Map<String, String> headers) {
        Map<String, Object> mergedPayload = new LinkedHashMap<>();
        if (payload != null && !payload.isEmpty()) {
            mergedPayload.putAll(payload);
        }
        if (headers != null && !headers.isEmpty()) {
            mergedPayload.put("_headers", new LinkedHashMap<>(headers));
        }
        return paymentCallbackService.previewNotify(providerType, mergedPayload);
    }

    @Transactional
    public Map<String, Object> mockPayVipOrder(Long orderId) {
        PaymentConfigService.PaymentResolvedSettings settings = paymentConfigService.getResolvedSettings();
        if (!settings.isMockEnabled()) {
            throw new RuntimeException("当前未开启支付 Mock 模式");
        }
        UserPlanOrder order = userPlanOrderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("VIP 订单不存在"));
        if ("PAID".equalsIgnoreCase(order.getStatus()) || "ACTIVE".equalsIgnoreCase(order.getStatus())) {
            return toVipOrderMap(order);
        }
        PaymentProviderType providerType = settings.getProviderType() == null ? PaymentProviderType.ALIPAY : settings.getProviderType();
        UserPlanOrder saved = vipOrderLifecycleService.markOrderPaid(
            order,
            providerType,
            "mock-" + order.getOrderNo(),
            Map.of("orderNo", order.getOrderNo(), "status", "PAID", "source", "SUPER_ADMIN_MOCK"),
            null
        );
        return toVipOrderMap(saved);
    }

    @Transactional
    public Map<String, Object> cancelVipOrder(Long orderId, String remark) {
        return toVipOrderMap(vipOrderAdminService.cancelOrder(orderId, remark));
    }

    @Transactional
    public Map<String, Object> refundVipOrder(Long orderId, Integer refundAmountFen, String remark) {
        UserPlanOrder order = vipOrderAdminService.refundOrder(orderId, refundAmountFen, remark);
        Map<String, Object> resp = toVipOrderMap(order);
        if ("REFUNDED".equalsIgnoreCase(order.getStatus())) {
            resp.put("message", "退款已完成");
        } else if ("REQUESTED".equalsIgnoreCase(order.getRefundStatus())) {
            resp.put("message", "退款请求已创建，等待支付平台回调或人工确认");
        } else {
            resp.put("message", "退款状态已更新");
        }
        return resp;
    }

    @Transactional
    public Map<String, Object> confirmVipOrderRefundSuccess(Long orderId, Integer refundAmountFen, String remark) {
        UserPlanOrder order = vipOrderAdminService.confirmRefundSuccess(orderId, refundAmountFen, remark);
        Map<String, Object> resp = toVipOrderMap(order);
        resp.put("message", "已人工确认退款成功");
        return resp;
    }

    @Transactional
    public Map<String, Object> markVipOrderRefundFailed(Long orderId, String remark) {
        UserPlanOrder order = vipOrderAdminService.markRefundFailed(orderId, remark);
        Map<String, Object> resp = toVipOrderMap(order);
        resp.put("message", "已标记为退款失败，可重新发起退款");
        return resp;
    }

    @Transactional
    public Map<String, Object> runLegacyDataMigration() {
        return legacyDataMigrationService.runMigration();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listVipPlans() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("vipPlans", vipPlanRepository.findAllByOrderBySortOrderAscIdAsc().stream()
            .map(this::toVipPlanMap)
            .collect(Collectors.toList()));
        return resp;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listVipOrders(Long userId,
                                             Integer page,
                                             Integer size,
                                             Boolean autoRenewEnabled,
                                             Boolean dueForRenewal) {
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        Map<String, Object> resp = new LinkedHashMap<>();
        if (Boolean.TRUE.equals(dueForRenewal)) {
            List<UserPlanOrder> dueOrders = userPlanOrderRepository
                .findByAutoRenewEnabledTrueAndNextRenewalAtLessThanEqualAndStatusInOrderByNextRenewalAtAscCreatedAtAsc(
                    LocalDateTime.now(),
                    List.of("PAID", "ACTIVE")
                )
                .stream()
                .filter(order -> userId == null || userId.equals(order.getUserId()))
                .collect(Collectors.toList());
            int fromIndex = Math.min(pageNumber * pageSize, dueOrders.size());
            int toIndex = Math.min(fromIndex + pageSize, dueOrders.size());
            List<Map<String, Object>> content = dueOrders.subList(fromIndex, toIndex).stream()
                .map(this::toVipOrderMap)
                .collect(Collectors.toList());
            int totalPages = dueOrders.isEmpty() ? 0 : (int) Math.ceil(dueOrders.size() / (double) pageSize);
            resp.put("content", content);
            resp.put("page", pageNumber);
            resp.put("size", pageSize);
            resp.put("totalElements", dueOrders.size());
            resp.put("totalPages", totalPages);
            resp.put("first", pageNumber <= 0);
            resp.put("last", toIndex >= dueOrders.size());
        } else {
            var pageable = PageRequest.of(pageNumber, pageSize);
            var result = Boolean.TRUE.equals(autoRenewEnabled)
                ? (userId == null
                    ? userPlanOrderRepository.findByAutoRenewEnabledTrueOrderByCreatedAtDesc(pageable)
                    : userPlanOrderRepository.findByUserIdAndAutoRenewEnabledTrueOrderByCreatedAtDesc(userId, pageable))
                : (userId == null
                    ? userPlanOrderRepository.findAllByOrderByCreatedAtDesc(pageable)
                    : userPlanOrderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable));
            List<Map<String, Object>> content = result.getContent().stream()
                .map(this::toVipOrderMap)
                .collect(Collectors.toList());
            resp.put("content", content);
            resp.put("page", result.getNumber());
            resp.put("size", result.getSize());
            resp.put("totalElements", result.getTotalElements());
            resp.put("totalPages", result.getTotalPages());
            resp.put("first", result.isFirst());
            resp.put("last", result.isLast());
        }
        resp.put("autoRenewEnabled", Boolean.TRUE.equals(autoRenewEnabled));
        resp.put("dueForRenewal", Boolean.TRUE.equals(dueForRenewal));
        return resp;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getVipOrderByOrderNo(String orderNo) {
        if (orderNo == null || orderNo.trim().isEmpty()) {
            throw new RuntimeException("订单号不能为空");
        }
        UserPlanOrder order = userPlanOrderRepository.findByOrderNo(orderNo.trim())
            .orElseThrow(() -> new RuntimeException("VIP 订单不存在"));
        return toVipOrderMap(order);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> previewVipRenewals(Integer limit) {
        return vipRenewalService.previewDueRenewals(limit);
    }

    @Transactional
    public Map<String, Object> executeVipRenewals(Integer limit) {
        return vipRenewalService.executeDueRenewals(limit);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listUsers(Integer page, Integer size, String keyword) {
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? 10 : Math.min(size, 100);
        var result = userAccountRepository.search(parseNullableString(keyword), PageRequest.of(pageNumber, pageSize));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("content", result.getContent().stream().map(this::toUserMap).collect(Collectors.toList()));
        resp.put("page", result.getNumber());
        resp.put("size", result.getSize());
        resp.put("totalElements", result.getTotalElements());
        resp.put("totalPages", result.getTotalPages());
        resp.put("first", result.isFirst());
        resp.put("last", result.isLast());
        return resp;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listLoginRecords(Long userId, Integer page, Integer size) {
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        var result = userId == null
            ? loginRecordRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(pageNumber, pageSize))
            : loginRecordRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(pageNumber, pageSize));

        Map<Long, UserAccountRepository.UserIdentityProjection> usersById = loadUserIdentityMap(
            result.getContent().stream().map(LoginRecord::getUserId).collect(Collectors.toSet())
        );

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("content", result.getContent().stream()
            .map(record -> toLoginRecordMap(record, usersById.get(record.getUserId())))
            .collect(Collectors.toList()));
        resp.put("page", result.getNumber());
        resp.put("size", result.getSize());
        resp.put("totalElements", result.getTotalElements());
        resp.put("totalPages", result.getTotalPages());
        resp.put("first", result.isFirst());
        resp.put("last", result.isLast());
        return resp;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listOperationLogs(Long userId, Integer page, Integer size) {
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        var result = userId == null
            ? operationLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(pageNumber, pageSize))
            : operationLogRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(pageNumber, pageSize));

        Map<Long, UserAccountRepository.UserIdentityProjection> usersById = loadUserIdentityMap(
            result.getContent().stream().map(com.photoexhibition.entity.OperationLog::getUserId).collect(Collectors.toSet())
        );

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("content", result.getContent().stream()
            .map(log -> toOperationLogMap(log, usersById.get(log.getUserId())))
            .collect(Collectors.toList()));
        resp.put("page", result.getNumber());
        resp.put("size", result.getSize());
        resp.put("totalElements", result.getTotalElements());
        resp.put("totalPages", result.getTotalPages());
        resp.put("first", result.isFirst());
        resp.put("last", result.isLast());
        return resp;
    }

    @Transactional
    public Map<String, Object> updateUser(Long userId, Map<String, Object> request) {
        UserAccount user = userAccountRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (request.containsKey("nickname")) {
            user.setNickname(parseNullableString(request.get("nickname")));
        }
        if (request.containsKey("phone")) {
            String phone = parseNullableString(request.get("phone"));
            if (phone != null && !phone.matches("^1\\d{10}$")) {
                throw new RuntimeException("手机号格式不正确");
            }
            if (phone != null) {
                userAccountRepository.findByPhone(phone)
                    .filter(existing -> !existing.getId().equals(userId))
                    .ifPresent(existing -> {
                        throw new RuntimeException("手机号已被其他用户使用");
                    });
            }
            user.setPhone(phone);
        }
        if (request.containsKey("projectNameZh")) {
            user.setProjectNameZh(parseNullableString(request.get("projectNameZh")));
        }
        if (request.containsKey("email")) {
            String email = parseNullableString(request.get("email"));
            if (email != null) {
                String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
                if (!normalizedEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                    throw new RuntimeException("邮箱格式不正确");
                }
                userAccountRepository.findByEmail(normalizedEmail)
                    .filter(existing -> !existing.getId().equals(userId))
                    .ifPresent(existing -> {
                        throw new RuntimeException("邮箱已被其他用户使用");
                    });
                user.setEmail(normalizedEmail);
            } else {
                user.setEmail(null);
            }
        }
        if (request.containsKey("projectNameEn")) {
            user.setProjectNameEn(parseNullableString(request.get("projectNameEn")));
        }
        if (request.containsKey("storageQuotaBytes")) {
            user.setStorageQuotaBytes(parseLong(request.get("storageQuotaBytes"), "storageQuotaBytes"));
        }
        if (request.containsKey("vipExtraQuotaBytes")) {
            user.setVipExtraQuotaBytes(parseLong(request.get("vipExtraQuotaBytes"), "vipExtraQuotaBytes"));
        }
        if (request.containsKey("currentVipPlanId")) {
            Long planId = parseLongObject(request.get("currentVipPlanId"), "currentVipPlanId");
            if (planId != null && !vipPlanRepository.existsById(planId)) {
                throw new RuntimeException("VIP 套餐不存在");
            }
            user.setCurrentVipPlanId(planId);
        }
        if (request.containsKey("vipExpireAt")) {
            user.setVipExpireAt(parseDateTime(request.get("vipExpireAt"), "vipExpireAt"));
        }
        if (request.containsKey("preferredStorageProviderId")) {
            Long providerId = parseLongObject(request.get("preferredStorageProviderId"), "preferredStorageProviderId");
            if (providerId != null) {
                if (!storageProviderRepository.existsById(providerId)) {
                    throw new RuntimeException("默认存储提供者不存在");
                }
                storageProviderService.resolveUploadProvider(user, providerId);
            }
            user.setPreferredStorageProviderId(providerId);
        }
        if (request.containsKey("multiUserVisible")) {
            user.setMultiUserVisible(parseBoolean(request.get("multiUserVisible"), "multiUserVisible"));
        }
        if (request.containsKey("phoneVerified")) {
            user.setPhoneVerified(parseBoolean(request.get("phoneVerified"), "phoneVerified"));
        }
        if (request.containsKey("emailVerified")) {
            user.setEmailVerified(parseBoolean(request.get("emailVerified"), "emailVerified"));
        }
        if (request.containsKey("role")) {
            UserRole role = UserRole.valueOf(parseRequiredString(request.get("role"), "role").toUpperCase());
            user.setRole(role);
        }
        if (request.containsKey("status")) {
            UserStatus status = UserStatus.valueOf(parseRequiredString(request.get("status"), "status").toUpperCase());
            user.setStatus(status);
            if (status != UserStatus.LOCKED) {
                user.setLockedUntil(null);
            } else if (user.getLockedUntil() == null) {
                user.setLockedUntil(LocalDateTime.now().plusYears(100));
            }
        }

        userAccountRepository.save(user);
        return toUserMap(user);
    }

    @Transactional
    public Map<String, Object> resetUserPassword(Long userId, String newPassword) {
        UserAccount user = userAccountRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        String trimmedPassword = newPassword == null ? "" : newPassword.trim();
        if (trimmedPassword.length() < 6) {
            throw new RuntimeException("新密码长度不能少于6位");
        }
        user.setPassword(passwordEncoder.encode(trimmedPassword));
        user.setLoginAttempts(0);
        user.setLockedUntil(null);
        userAccountRepository.save(user);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("userId", user.getId());
        resp.put("username", user.getUsername());
        resp.put("message", "用户密码已重置");
        return resp;
    }

    @Transactional
    public Map<String, Object> createVipPlan(Map<String, Object> request) {
        VipPlan plan = new VipPlan();
        applyVipPlanRequest(plan, request, true);
        return toVipPlanMap(vipPlanRepository.save(plan));
    }

    @Transactional
    public Map<String, Object> updateVipPlan(Long planId, Map<String, Object> request) {
        VipPlan plan = vipPlanRepository.findById(planId)
            .orElseThrow(() -> new RuntimeException("VIP 套餐不存在"));
        applyVipPlanRequest(plan, request, false);
        return toVipPlanMap(vipPlanRepository.save(plan));
    }

    @Transactional
    public Map<String, Object> createVipOrder(Map<String, Object> request) {
        UserPlanOrder order = new UserPlanOrder();
        applyVipOrderRequest(order, request, true);
        order.setOrderNo(vipOrderLifecycleService.generateOrderNo());
        UserPlanOrder saved = userPlanOrderRepository.save(order);
        vipOrderLifecycleService.applyOrderToUserIfNeeded(saved);
        return toVipOrderMap(saved);
    }

    @Transactional
    public Map<String, Object> updateVipOrder(Long orderId, Map<String, Object> request) {
        UserPlanOrder order = userPlanOrderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("VIP 订单不存在"));
        applyVipOrderRequest(order, request, false);
        UserPlanOrder saved = userPlanOrderRepository.save(order);
        vipOrderLifecycleService.applyOrderToUserIfNeeded(saved);
        return toVipOrderMap(saved);
    }

    @Transactional
    public List<Map<String, Object>> listStorageProviders() {
        storageProviderService.ensureDefaultLocalStorageProvider();
        return storageProviderRepository.findAllByOrderByPriorityAscIdAsc().stream()
            .map(this::toStorageProviderMap)
            .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> createStorageProvider(Map<String, Object> request) {
        StorageProvider provider = new StorageProvider();
        applyStorageProvider(provider, request, true, true);
        return toStorageProviderMap(storageProviderRepository.save(provider));
    }

    @Transactional
    public Map<String, Object> updateStorageProvider(Long providerId, Map<String, Object> request) {
        StorageProvider provider = storageProviderRepository.findById(providerId)
            .orElseThrow(() -> new RuntimeException("存储提供者不存在"));
        applyStorageProvider(provider, request, false, true);
        return toStorageProviderMap(storageProviderRepository.save(provider));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> testStorageProvider(Map<String, Object> request) {
        StorageProvider provider = new StorageProvider();
        applyStorageProvider(provider, request, true, false);
        return storageProviderService.testProviderAvailability(provider, null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> previewStorageMigration(Map<String, Object> request) {
        return storageMigrationService.previewMigration(request);
    }

    @Transactional
    public Map<String, Object> executeStorageMigration(Map<String, Object> request) throws Exception {
        return storageMigrationService.executeMigration(request);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> previewStorageCleanup(Map<String, Object> request) {
        return storageMigrationService.previewCleanup(request);
    }

    @Transactional
    public Map<String, Object> executeStorageCleanup(Map<String, Object> request) throws Exception {
        return storageMigrationService.executeCleanup(request);
    }

    private void applyStorageProvider(StorageProvider provider, Map<String, Object> request, boolean creating, boolean validateNameUniqueness) {
        String name = parseRequiredString(request.get("name"), "name");
        if (validateNameUniqueness) {
            storageProviderRepository.findByName(name)
                .filter(existing -> !Objects.equals(existing.getId(), provider.getId()))
                .ifPresent(existing -> {
                    throw new RuntimeException("存储提供者名称已存在");
                });
        }

        provider.setName(name);
        provider.setType(StorageType.valueOf(parseRequiredString(request.get("type"), "type").toUpperCase()));
        provider.setEnabled(request.containsKey("enabled")
            ? parseBoolean(request.get("enabled"), "enabled")
            : (creating || provider.getEnabled() == null ? Boolean.TRUE : provider.getEnabled()));
        provider.setPriority(request.containsKey("priority")
            ? parseInteger(request.get("priority"), "priority")
            : (provider.getPriority() == null ? 100 : provider.getPriority()));
        provider.setEndpoint(parseNullableString(request.get("endpoint")));
        provider.setBucketName(parseNullableString(request.get("bucketName")));
        provider.setBaseDirectory(parseNullableString(request.get("baseDirectory")));
        provider.setConfigJson(parseNullableString(request.get("configJson")));
        normalizeStorageProviderFields(provider);

        boolean isDefault = request.containsKey("isDefault")
            ? parseBoolean(request.get("isDefault"), "isDefault")
            : Boolean.TRUE.equals(provider.getIsDefault());

        if (isDefault && !Boolean.TRUE.equals(provider.getEnabled())) {
            provider.setEnabled(true);
        }
        provider.setIsDefault(isDefault);

        if (provider.getType() == StorageType.LOCAL && (provider.getBaseDirectory() == null || provider.getBaseDirectory().isBlank())) {
            provider.setBaseDirectory(systemConfigService.getLocalStorageRoot());
        }

        validateStorageProvider(provider);

        if (isDefault) {
            unsetOtherDefaults(provider.getId());
        } else if (storageProviderRepository.findFirstByIsDefaultTrue().isEmpty()) {
            provider.setIsDefault(true);
        }
    }

    private void normalizeStorageProviderFields(StorageProvider provider) {
        StorageType type = provider.getType();
        if (type == null) {
            return;
        }
        if (type == StorageType.COS) {
            inferCosFieldsFromEndpoint(provider);
            provider.setEndpoint(normalizeCosEndpoint(provider));
        }
        if (!requiresEndpoint(type) && type != StorageType.COS) {
            provider.setEndpoint(null);
        }
        if (!requiresBucket(type)) {
            provider.setBucketName(null);
        }
        if (!requiresBaseDirectory(type)) {
            provider.setBaseDirectory(null);
        }
    }

    private void validateStorageProvider(StorageProvider provider) {
        StorageType type = provider.getType();
        if (type == null) {
            throw new RuntimeException("存储类型不能为空");
        }

        boolean requiresEndpoint = requiresEndpoint(type);
        boolean requiresBucket = requiresBucket(type);
        boolean requiresBaseDirectory = requiresBaseDirectory(type);

        if (requiresEndpoint && (provider.getEndpoint() == null || provider.getEndpoint().isBlank())) {
            throw new RuntimeException(type.name() + " 存储缺少 endpoint 配置");
        }
        if (requiresBucket && (provider.getBucketName() == null || provider.getBucketName().isBlank())) {
            throw new RuntimeException(type.name() + " 存储缺少 bucketName / 容器配置");
        }
        if (requiresBaseDirectory && (provider.getBaseDirectory() == null || provider.getBaseDirectory().isBlank())) {
            throw new RuntimeException(type.name() + " 存储缺少 baseDirectory 配置");
        }
    }

    private String normalizeCosEndpoint(StorageProvider provider) {
        String raw = parseNullableString(provider.getEndpoint());
        if (raw == null) {
            return buildDefaultCosEndpoint(parseCosRegion(provider));
        }
        String normalized = raw.trim();
        if (!normalized.contains("://")) {
            normalized = "https://" + normalized;
        }
        try {
            URI uri = URI.create(normalized);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return raw.trim();
            }
            String bucket = parseNullableString(provider.getBucketName());
            if (bucket != null) {
                String prefix = bucket.toLowerCase() + ".";
                if (host.toLowerCase().startsWith(prefix)) {
                    host = host.substring(prefix.length());
                }
            }
            URI cleaned = new URI(
                uri.getScheme() == null ? "https" : uri.getScheme(),
                uri.getUserInfo(),
                host,
                uri.getPort(),
                null,
                null,
                null
            );
            return cleaned.toString();
        } catch (Exception ignored) {
            return raw.trim();
        }
    }

    private String parseCosRegion(StorageProvider provider) {
        Map<String, Object> config = parseObject(provider.getConfigJson());
        return parseNullableString(config.get("region"));
    }

    private String buildDefaultCosEndpoint(String region) {
        String normalizedRegion = parseNullableString(region);
        return normalizedRegion == null ? null : "https://cos." + normalizedRegion + ".myqcloud.com";
    }

    private Map<String, Object> parseObject(String rawJson) {
        String raw = parseNullableString(rawJson);
        if (raw == null) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            return new HashMap<>();
        }
    }

    private void inferCosFieldsFromEndpoint(StorageProvider provider) {
        String endpoint = parseNullableString(provider.getEndpoint());
        if (endpoint == null) {
            return;
        }
        String normalized = endpoint.contains("://") ? endpoint : "https://" + endpoint;
        try {
            URI uri = URI.create(normalized);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return;
            }
            String[] parts = host.split("\\.");
            if (parts.length < 4) {
                return;
            }
            String first = parts[0];
            if (provider.getBucketName() == null || provider.getBucketName().isBlank()) {
                if (!"cos".equalsIgnoreCase(first) && first.contains("-")) {
                    provider.setBucketName(first);
                }
            }
            String region = parts[0].equalsIgnoreCase("cos") ? parts[1] : (parts.length > 2 ? parts[2] : null);
            if (region != null && !region.isBlank()) {
                Map<String, Object> config = parseObject(provider.getConfigJson());
                if (parseNullableString(config.get("region")) == null) {
                    config.put("region", region);
                    try {
                        provider.setConfigJson(objectMapper.writeValueAsString(config));
                    } catch (Exception ignored) {
                        // keep original configJson if serialization fails
                    }
                }
            }
        } catch (Exception ignored) {
            // ignore malformed endpoint and keep existing values
        }
    }

    private boolean requiresEndpoint(StorageType type) {
        return type != StorageType.LOCAL
            && type != StorageType.COS
            && type != StorageType.SFTP
            && type != StorageType.SMB
            && type != StorageType.NFS;
    }

    private boolean requiresBucket(StorageType type) {
        return type == StorageType.COS
            || type == StorageType.S3_COMPATIBLE
            || type == StorageType.MINIO
            || type == StorageType.OSS
            || type == StorageType.R2
            || type == StorageType.AZURE_BLOB
            || type == StorageType.GCS
            || type == StorageType.OBS
            || type == StorageType.TOS
            || type == StorageType.BOS
            || type == StorageType.UCLOUD_US3
            || type == StorageType.JD_JSS
            || type == StorageType.WASABI
            || type == StorageType.QINIU_KODO
            || type == StorageType.B2
            || type == StorageType.UPYUN;
    }

    private boolean requiresBaseDirectory(StorageType type) {
        return type == StorageType.LOCAL
            || type == StorageType.FTP
            || type == StorageType.WEBDAV
            || type == StorageType.COS
            || type == StorageType.SFTP
            || type == StorageType.S3_COMPATIBLE
            || type == StorageType.MINIO
            || type == StorageType.OSS
            || type == StorageType.R2
            || type == StorageType.SMB
            || type == StorageType.NFS
            || type == StorageType.AZURE_BLOB
            || type == StorageType.GCS
            || type == StorageType.OBS
            || type == StorageType.TOS
            || type == StorageType.BOS
            || type == StorageType.UCLOUD_US3
            || type == StorageType.JD_JSS
            || type == StorageType.WASABI
            || type == StorageType.QINIU_KODO
            || type == StorageType.B2
            || type == StorageType.UPYUN
            || type == StorageType.DROPBOX
            || type == StorageType.ONEDRIVE;
    }

    private void unsetOtherDefaults(Long selfId) {
        storageProviderRepository.findAllByOrderByPriorityAscIdAsc().stream()
            .filter(existing -> Boolean.TRUE.equals(existing.getIsDefault()))
            .filter(existing -> selfId == null || !existing.getId().equals(selfId))
            .forEach(existing -> {
                existing.setIsDefault(false);
                storageProviderRepository.save(existing);
            });
    }

    private void setDefaultStorageProvider(Long providerId) {
        StorageProvider target = storageProviderRepository.findById(providerId)
            .orElseThrow(() -> new RuntimeException("默认存储提供者不存在"));
        if (!Boolean.TRUE.equals(target.getEnabled())) {
            target.setEnabled(true);
        }
        unsetOtherDefaults(target.getId());
        target.setIsDefault(true);
        storageProviderRepository.save(target);
    }

    private Map<String, Object> toUserMap(UserAccount user) {
        Map<String, Object> resp = new LinkedHashMap<>();
        StorageProvider preferredProvider = null;
        VipPlan vipPlan = null;
        if (user.getPreferredStorageProviderId() != null) {
            preferredProvider = storageProviderRepository.findById(user.getPreferredStorageProviderId()).orElse(null);
        }
        if (user.getCurrentVipPlanId() != null) {
            vipPlan = vipPlanRepository.findById(user.getCurrentVipPlanId()).orElse(null);
        }
        resp.put("id", user.getId());
        resp.put("slug", user.getSlug());
        resp.put("username", user.getUsername());
        resp.put("phone", user.getPhone());
        resp.put("email", user.getEmail());
        resp.put("nickname", user.getNickname());
        resp.put("role", user.getRole().name());
        resp.put("status", user.getStatus().name());
        resp.put("phoneVerified", Boolean.TRUE.equals(user.getPhoneVerified()));
        resp.put("emailVerified", Boolean.TRUE.equals(user.getEmailVerified()));
        resp.put("storageQuotaBytes", defaultLong(user.getStorageQuotaBytes()));
        resp.put("vipExtraQuotaBytes", defaultLong(user.getVipExtraQuotaBytes()));
        resp.put("currentVipPlanId", user.getCurrentVipPlanId());
        resp.put("currentVipPlanName", vipPlan != null ? vipPlan.getName() : null);
        resp.put("currentVipPlanCode", vipPlan != null ? vipPlan.getCode() : null);
        resp.put("vipPlanExtraQuotaBytes", vipPlan != null ? defaultLong(vipPlan.getExtraQuotaBytes()) : 0L);
        resp.put("vipExpireAt", user.getVipExpireAt());
        resp.put("effectiveStorageQuotaBytes", userStorageService.getEffectiveQuotaBytes(user));
        resp.put("storageUsedBytes", defaultLong(user.getStorageUsedBytes()));
        resp.put("remainingStorageBytes", Math.max(0L, userStorageService.getEffectiveQuotaBytes(user) - defaultLong(user.getStorageUsedBytes())));
        resp.put("preferredStorageProviderId", user.getPreferredStorageProviderId());
        resp.put("preferredStorageProviderName", preferredProvider != null ? preferredProvider.getName() : null);
        resp.put("preferredStorageProviderType", preferredProvider != null ? preferredProvider.getType().name() : null);
        resp.put("multiUserVisible", Boolean.TRUE.equals(user.getMultiUserVisible()));
        resp.put("projectNameZh", user.getProjectNameZh());
        resp.put("projectNameEn", user.getProjectNameEn());
        resp.put("lastLoginAt", user.getLastLoginAt());
        resp.put("lastLoginIp", user.getLastLoginIp());
        resp.put("createdAt", user.getCreatedAt());
        resp.put("updatedAt", user.getUpdatedAt());
        return resp;
    }

    private Map<String, Object> toVipPlanMap(VipPlan plan) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", plan.getId());
        resp.put("code", plan.getCode());
        resp.put("name", plan.getName());
        resp.put("description", plan.getDescription());
        resp.put("extraQuotaBytes", defaultLong(plan.getExtraQuotaBytes()));
        resp.put("durationDays", plan.getDurationDays() == null ? 30 : plan.getDurationDays());
        resp.put("priceFen", plan.getPriceFen() == null ? 0 : plan.getPriceFen());
        resp.put("planCategory", plan.getPlanCategory());
        resp.put("quotaGrantMode", plan.getQuotaGrantMode());
        resp.put("stackingMode", plan.getStackingMode());
        resp.put("enabled", Boolean.TRUE.equals(plan.getEnabled()));
        resp.put("sortOrder", plan.getSortOrder() == null ? 100 : plan.getSortOrder());
        resp.put("createdAt", plan.getCreatedAt());
        resp.put("updatedAt", plan.getUpdatedAt());
        return resp;
    }

    private Map<String, Object> toVipOrderMap(UserPlanOrder order) {
        Map<String, Object> resp = new LinkedHashMap<>();
        UserAccount user = userAccountRepository.findById(order.getUserId()).orElse(null);
        VipPlan plan = vipPlanRepository.findById(order.getVipPlanId()).orElse(null);
        UserPlanOrder renewalSourceOrder = order.getRenewalSourceOrderId() == null
            ? null
            : userPlanOrderRepository.findById(order.getRenewalSourceOrderId()).orElse(null);
        UserPlanOrder renewalChildOrder = order.getId() == null
            ? null
            : userPlanOrderRepository.findFirstByRenewalSourceOrderIdOrderByCreatedAtDesc(order.getId()).orElse(null);
        resp.put("id", order.getId());
        resp.put("orderNo", order.getOrderNo());
        resp.put("userId", order.getUserId());
        resp.put("username", user != null ? user.getUsername() : null);
        resp.put("nickname", user != null ? user.getNickname() : null);
        resp.put("userSlug", user != null ? user.getSlug() : null);
        resp.put("vipPlanId", order.getVipPlanId());
        resp.put("vipPlanName", plan != null ? plan.getName() : null);
        resp.put("vipPlanCode", plan != null ? plan.getCode() : null);
        resp.put("amountFen", order.getAmountFen());
        resp.put("originalAmountFen", order.getOriginalAmountFen());
        resp.put("creditedAmountFen", order.getCreditedAmountFen());
        resp.put("changeType", order.getChangeType());
        resp.put("sourceVipPlanId", order.getSourceVipPlanId());
        resp.put("status", order.getStatus());
        resp.put("source", order.getSource());
        resp.put("paymentProviderType", order.getPaymentProviderType());
        resp.put("externalTradeNo", order.getExternalTradeNo());
        resp.put("gatewayStatus", order.getGatewayStatus());
        resp.put("callbackPayloadJson", order.getCallbackPayloadJson());
        resp.put("paymentNotifiedAt", order.getPaymentNotifiedAt());
        resp.put("paidAt", order.getPaidAt());
        resp.put("cancelledAt", order.getCancelledAt());
        resp.put("refundStatus", order.getRefundStatus());
        resp.put("refundAmountFen", order.getRefundAmountFen());
        resp.put("refundedAt", order.getRefundedAt());
        resp.put("autoRenewEnabled", Boolean.TRUE.equals(order.getAutoRenewEnabled()));
        resp.put("nextRenewalAt", order.getNextRenewalAt());
        resp.put("renewalSourceOrderId", order.getRenewalSourceOrderId());
        resp.put("renewalSourceOrderNo", renewalSourceOrder != null ? renewalSourceOrder.getOrderNo() : null);
        resp.put("renewalSourceOrderStatus", renewalSourceOrder != null ? renewalSourceOrder.getStatus() : null);
        resp.put("renewalChildOrderId", renewalChildOrder != null ? renewalChildOrder.getId() : null);
        resp.put("renewalChildOrderNo", renewalChildOrder != null ? renewalChildOrder.getOrderNo() : null);
        resp.put("renewalChildOrderStatus", renewalChildOrder != null ? renewalChildOrder.getStatus() : null);
        resp.put("canInitiatePayment", canInitiatePayment(order));
        resp.put("canToggleAutoRenew", canToggleAutoRenew(order));
        resp.put("orderStageLabel", resolveOrderStageLabel(order));
        resp.put("renewalChainType", order.getRenewalSourceOrderId() == null ? "PRIMARY" : "RENEWAL_CHILD");
        resp.put("dueForRenewal", isDueForRenewal(order));
        resp.put("expireAt", order.getExpireAt());
        resp.put("pricingDetailJson", order.getPricingDetailJson());
        resp.put("remark", order.getRemark());
        resp.put("createdAt", order.getCreatedAt());
        resp.put("updatedAt", order.getUpdatedAt());
        return resp;
    }

    private boolean isDueForRenewal(UserPlanOrder order) {
        return Boolean.TRUE.equals(order.getAutoRenewEnabled())
            && order.getNextRenewalAt() != null
            && !order.getNextRenewalAt().isAfter(LocalDateTime.now())
            && ("PAID".equalsIgnoreCase(order.getStatus()) || "ACTIVE".equalsIgnoreCase(order.getStatus()));
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

    private Map<String, Object> toStorageProviderMap(StorageProvider provider) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", provider.getId());
        resp.put("name", provider.getName());
        resp.put("type", provider.getType().name());
        resp.put("enabled", Boolean.TRUE.equals(provider.getEnabled()));
        resp.put("isDefault", Boolean.TRUE.equals(provider.getIsDefault()));
        resp.put("priority", provider.getPriority());
        resp.put("endpoint", provider.getEndpoint());
        resp.put("bucketName", provider.getBucketName());
        resp.put("baseDirectory", provider.getBaseDirectory());
        resp.put("configJson", provider.getConfigJson());
        resp.put("createdAt", provider.getCreatedAt());
        resp.put("updatedAt", provider.getUpdatedAt());
        resp.putAll(storageProviderService.describeProviderCapabilities(provider));
        return resp;
    }

    private Map<Long, UserAccountRepository.UserIdentityProjection> loadUserIdentityMap(Collection<Long> userIds) {
        Set<Long> filteredUserIds = userIds == null
            ? Set.of()
            : userIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        if (filteredUserIds.isEmpty()) {
            return Map.of();
        }
        return userAccountRepository.findIdentityByIdIn(filteredUserIds).stream()
            .collect(Collectors.toMap(UserAccountRepository.UserIdentityProjection::getId, user -> user, (left, right) -> right));
    }

    private long resolveEffectiveQuotaBytes(UserAccountRepository.UserOverviewProjection user, Map<Long, Long> enabledPlanQuotaById) {
        if (user == null) {
            return 0L;
        }
        long baseQuota = defaultLong(user.getStorageQuotaBytes());
        long vipExtraQuota = defaultLong(user.getVipExtraQuotaBytes());
        long planQuota = 0L;
        if (user.getCurrentVipPlanId() != null
            && (user.getVipExpireAt() == null || !user.getVipExpireAt().isBefore(LocalDateTime.now()))) {
            planQuota = Math.max(0L, enabledPlanQuotaById.getOrDefault(user.getCurrentVipPlanId(), 0L));
        }
        return Math.max(0L, baseQuota + vipExtraQuota + planQuota);
    }

    private Map<String, Object> toLoginRecordMap(LoginRecord record, UserAccountRepository.UserIdentityProjection user) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", record.getId());
        resp.put("userId", record.getUserId());
        resp.put("usernameSnapshot", record.getUsernameSnapshot());
        resp.put("phoneSnapshot", record.getPhoneSnapshot());
        resp.put("loginMethod", record.getLoginMethod() != null ? record.getLoginMethod().name() : null);
        resp.put("success", Boolean.TRUE.equals(record.getSuccess()));
        resp.put("ipAddress", record.getIpAddress());
        resp.put("userAgent", record.getUserAgent());
        resp.put("failureReason", record.getFailureReason());
        resp.put("createdAt", record.getCreatedAt());
        resp.put("userSlug", user != null ? user.getSlug() : null);
        resp.put("nickname", user != null ? user.getNickname() : null);
        return resp;
    }

    private Map<String, Object> toOperationLogMap(com.photoexhibition.entity.OperationLog log, UserAccountRepository.UserIdentityProjection user) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", log.getId());
        resp.put("userId", log.getUserId());
        resp.put("username", user != null ? user.getUsername() : log.getOperatorUsername());
        resp.put("nickname", user != null ? user.getNickname() : null);
        resp.put("userSlug", user != null ? user.getSlug() : null);
        resp.put("operatorUsername", log.getOperatorUsername());
        resp.put("operationType", log.getOperationType() != null ? log.getOperationType().name() : null);
        resp.put("targetType", log.getTargetType());
        resp.put("targetId", log.getTargetId());
        resp.put("targetPath", sanitizeDisplayPath(log.getTargetPath()));
        resp.put("detailJson", sanitizeDetailJson(log.getDetailJson()));
        resp.put("ipAddress", log.getIpAddress());
        resp.put("createdAt", log.getCreatedAt());
        return resp;
    }

    private String sanitizeDisplayPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return rawPath;
        }
        String displayPath = userPathService.toDisplayPath(rawPath, true);
        if (!rawPath.equals(displayPath)) {
            return displayPath;
        }
        if (rawPath.startsWith("/") || rawPath.matches("^[A-Za-z]:\\\\.*")) {
            String normalized = rawPath.replace('\\', '/');
            int index = normalized.lastIndexOf('/');
            return index >= 0 ? normalized.substring(index + 1) : normalized;
        }
        return rawPath;
    }

    private String sanitizeDetailJson(String detailJson) {
        if (detailJson == null || detailJson.isBlank()) {
            return detailJson;
        }
        try {
            Object value = objectMapper.readValue(detailJson, new TypeReference<Object>() {});
            return objectMapper.writeValueAsString(sanitizeJsonValue(value));
        } catch (Exception ignored) {
            return sanitizePotentialPathText(detailJson);
        }
    }

    private Object sanitizeJsonValue(Object value) {
        if (value instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) value;
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> sanitized.put(String.valueOf(key), sanitizeJsonValue(nestedValue)));
            return sanitized;
        }
        if (value instanceof List<?>) {
            List<?> list = (List<?>) value;
            List<Object> sanitized = new ArrayList<>(list.size());
            list.forEach(item -> sanitized.add(sanitizeJsonValue(item)));
            return sanitized;
        }
        if (value instanceof String) {
            return sanitizePotentialPathText((String) value);
        }
        return value;
    }

    private String sanitizePotentialPathText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        if (text.startsWith("storage://") || text.startsWith("/") || text.matches("^[A-Za-z]:\\\\.*")) {
            return sanitizeDisplayPath(text);
        }
        return text;
    }

    private void applyVipPlanRequest(VipPlan plan, Map<String, Object> request, boolean creating) {
        if (creating || request.containsKey("code")) {
            String code = parseRequiredString(request.get("code"), "code").trim().toUpperCase();
            vipPlanRepository.findByCode(code)
                .filter(existing -> plan.getId() == null || !existing.getId().equals(plan.getId()))
                .ifPresent(existing -> {
                    throw new RuntimeException("VIP 套餐编码已存在");
                });
            plan.setCode(code);
        }
        if (creating || request.containsKey("name")) {
            plan.setName(parseRequiredString(request.get("name"), "name"));
        }
        if (request.containsKey("description")) {
            plan.setDescription(parseNullableString(request.get("description")));
        }
        if (creating || request.containsKey("extraQuotaBytes")) {
            plan.setExtraQuotaBytes(parseLong(request.get("extraQuotaBytes"), "extraQuotaBytes"));
        }
        if (creating || request.containsKey("durationDays")) {
            plan.setDurationDays(parseInteger(request.get("durationDays"), "durationDays"));
        }
        if (creating || request.containsKey("priceFen")) {
            plan.setPriceFen(parseInteger(request.get("priceFen"), "priceFen"));
        }
        if (request.containsKey("enabled")) {
            plan.setEnabled(parseBoolean(request.get("enabled"), "enabled"));
        }
        if (request.containsKey("sortOrder")) {
            plan.setSortOrder(parseInteger(request.get("sortOrder"), "sortOrder"));
        }
        if (request.containsKey("planCategory")) {
            plan.setPlanCategory(parseRequiredString(request.get("planCategory"), "planCategory").trim().toUpperCase());
        } else if (creating && plan.getPlanCategory() == null) {
            plan.setPlanCategory("STANDARD");
        }
        if (request.containsKey("quotaGrantMode")) {
            plan.setQuotaGrantMode(parseRequiredString(request.get("quotaGrantMode"), "quotaGrantMode").trim().toUpperCase());
        } else if (creating && plan.getQuotaGrantMode() == null) {
            plan.setQuotaGrantMode("FIXED_TERM");
        }
        if (request.containsKey("stackingMode")) {
            plan.setStackingMode(parseRequiredString(request.get("stackingMode"), "stackingMode").trim().toUpperCase());
        } else if (creating && plan.getStackingMode() == null) {
            plan.setStackingMode("REPLACE_OR_EXTEND");
        }
    }

    private void applyVipOrderRequest(UserPlanOrder order, Map<String, Object> request, boolean creating) {
        if (creating || request.containsKey("userId")) {
            Long userId = parseLongObject(request.get("userId"), "userId");
            if (userId == null || !userAccountRepository.existsById(userId)) {
                throw new RuntimeException("订单用户不存在");
            }
            order.setUserId(userId);
        }
        if (creating || request.containsKey("vipPlanId")) {
            Long vipPlanId = parseLongObject(request.get("vipPlanId"), "vipPlanId");
            if (vipPlanId == null || !vipPlanRepository.existsById(vipPlanId)) {
                throw new RuntimeException("VIP 套餐不存在");
            }
            order.setVipPlanId(vipPlanId);
        }
        if (request.containsKey("amountFen") || creating) {
            Integer amountFen = parseInteger(request.get("amountFen"), "amountFen");
            if (amountFen < 0) {
                throw new RuntimeException("amountFen 不能小于 0");
            }
            order.setAmountFen(amountFen);
        }
        if (request.containsKey("originalAmountFen")) {
            Integer originalAmountFen = parseInteger(request.get("originalAmountFen"), "originalAmountFen");
            if (originalAmountFen < 0) {
                throw new RuntimeException("originalAmountFen 不能小于 0");
            }
            order.setOriginalAmountFen(originalAmountFen);
        } else if (creating && order.getOriginalAmountFen() == null) {
            order.setOriginalAmountFen(order.getAmountFen());
        }
        if (request.containsKey("creditedAmountFen")) {
            Integer creditedAmountFen = parseInteger(request.get("creditedAmountFen"), "creditedAmountFen");
            if (creditedAmountFen < 0) {
                throw new RuntimeException("creditedAmountFen 不能小于 0");
            }
            order.setCreditedAmountFen(creditedAmountFen);
        } else if (creating && order.getCreditedAmountFen() == null) {
            order.setCreditedAmountFen(0);
        }
        if (request.containsKey("changeType")) {
            order.setChangeType(parseRequiredString(request.get("changeType"), "changeType").trim().toUpperCase());
        } else if (creating && order.getChangeType() == null) {
            order.setChangeType("PURCHASE");
        }
        if (request.containsKey("sourceVipPlanId")) {
            Long sourceVipPlanId = parseLongObject(request.get("sourceVipPlanId"), "sourceVipPlanId");
            if (sourceVipPlanId != null && !vipPlanRepository.existsById(sourceVipPlanId)) {
                throw new RuntimeException("来源 VIP 套餐不存在");
            }
            order.setSourceVipPlanId(sourceVipPlanId);
        }
        if (request.containsKey("status") || creating) {
            order.setStatus(parseRequiredString(request.get("status"), "status").trim().toUpperCase());
        }
        if (request.containsKey("source")) {
            order.setSource(parseRequiredString(request.get("source"), "source").trim().toUpperCase());
        } else if (creating && order.getSource() == null) {
            order.setSource("MANUAL");
        }
        if (request.containsKey("paidAt")) {
            order.setPaidAt(parseDateTime(request.get("paidAt"), "paidAt"));
        }
        if (request.containsKey("expireAt")) {
            order.setExpireAt(parseDateTime(request.get("expireAt"), "expireAt"));
        }
        if (request.containsKey("autoRenewEnabled")) {
            order.setAutoRenewEnabled(parseBoolean(request.get("autoRenewEnabled"), "autoRenewEnabled"));
        } else if (creating && order.getAutoRenewEnabled() == null) {
            order.setAutoRenewEnabled(false);
        }
        if (request.containsKey("nextRenewalAt")) {
            order.setNextRenewalAt(parseDateTime(request.get("nextRenewalAt"), "nextRenewalAt"));
        }
        if (request.containsKey("remark")) {
            order.setRemark(parseNullableString(request.get("remark")));
        }
        if (request.containsKey("pricingDetailJson")) {
            order.setPricingDetailJson(parseNullableString(request.get("pricingDetailJson")));
        }
        if (order.getOriginalAmountFen() != null && order.getAmountFen() != null && order.getCreditedAmountFen() != null) {
            if (order.getCreditedAmountFen() > order.getOriginalAmountFen()) {
                throw new RuntimeException("creditedAmountFen 不能大于 originalAmountFen");
            }
            if (order.getAmountFen() > order.getOriginalAmountFen()) {
                throw new RuntimeException("amountFen 不能大于 originalAmountFen");
            }
        }
    }

    private String parseRequiredString(Object value, String fieldName) {
        String parsed = parseNullableString(value);
        if (parsed == null || parsed.isBlank()) {
            throw new RuntimeException(fieldName + " 不能为空");
        }
        return parsed;
    }

    private String parseNullableString(Object value) {
        if (value == null) {
            return null;
        }
        String parsed = String.valueOf(value).trim();
        return parsed.isEmpty() ? null : parsed;
    }

    private long parseLong(Object value, String fieldName) {
        Long parsed = parseLongObject(value, fieldName);
        if (parsed == null) {
            throw new RuntimeException(fieldName + " 不能为空");
        }
        if (parsed < 0) {
            throw new RuntimeException(fieldName + " 不能小于 0");
        }
        return parsed;
    }

    private Long parseLongObject(Object value, String fieldName) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new RuntimeException(fieldName + " 不是有效数字");
        }
    }

    private Integer parseInteger(Object value, String fieldName) {
        if (value == null || String.valueOf(value).isBlank()) {
            throw new RuntimeException(fieldName + " 不能为空");
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new RuntimeException(fieldName + " 不是有效数字");
        }
    }

    private LocalDateTime parseDateTime(Object value, String fieldName) {
        String parsed = parseNullableString(value);
        if (parsed == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(parsed);
        } catch (Exception e) {
            throw new RuntimeException(fieldName + " 不是有效时间");
        }
    }

    private boolean parseBoolean(Object value, String fieldName) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value == null) {
            throw new RuntimeException(fieldName + " 不能为空");
        }
        String parsed = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(parsed)) {
            return true;
        }
        if ("false".equalsIgnoreCase(parsed)) {
            return false;
        }
        throw new RuntimeException(fieldName + " 不是有效布尔值");
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
