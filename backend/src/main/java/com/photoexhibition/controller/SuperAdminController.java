package com.photoexhibition.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.dto.SendEmailRequest;
import com.photoexhibition.entity.OperationType;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.service.AuthService;
import com.photoexhibition.service.OperationLogService;
import com.photoexhibition.service.ModelManagementService;
import com.photoexhibition.service.SuperAdminService;
import com.photoexhibition.service.UserPathService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/admin/super-admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SuperAdminController {

    private static final Pattern EMBEDDED_PATH_PATTERN =
        Pattern.compile("(storage://[^\\s,;]+|[A-Za-z]:\\\\[^\\s,;]+|/(?:[^\\s,;])+)");

    private final AuthService authService;
    private final SuperAdminService superAdminService;
    private final OperationLogService operationLogService;
    private final ModelManagementService modelManagementService;
    private final UserPathService userPathService;
    private final ObjectMapper objectMapper;

    @GetMapping("/overview")
    public ResponseEntity<?> overview(@RequestHeader("Authorization") String authorization) {
        return handle(authorization, () -> superAdminService.getOverview());
    }

    @GetMapping("/processing-overview")
    public ResponseEntity<?> processingOverview(@RequestHeader("Authorization") String authorization) {
        return handle(authorization, () -> superAdminService.getProcessingOverview());
    }

    @GetMapping("/settings")
    public ResponseEntity<?> settings(@RequestHeader("Authorization") String authorization) {
        return handle(authorization, () -> superAdminService.getSettings());
    }

    @PutMapping("/settings")
    public ResponseEntity<?> updateSettings(@RequestHeader("Authorization") String authorization,
                                            HttpServletRequest requestContext,
                                            @RequestBody Map<String, Object> request) {
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result = superAdminService.updateSettings(request);
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "updateSettings");
            detail.put("payload", request);
            operationLogService.log(operator, OperationType.CONFIG_UPDATE, "SYSTEM_SETTINGS", null, null, detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @GetMapping("/table-preferences")
    public ResponseEntity<?> tablePreferences(@RequestHeader("Authorization") String authorization) {
        return handle(authorization, superAdminService::getTablePreferences);
    }

    @PutMapping("/table-preferences")
    public ResponseEntity<?> updateTablePreferences(@RequestHeader("Authorization") String authorization,
                                                    HttpServletRequest requestContext,
                                                    @RequestBody(required = false) Map<String, Object> request) {
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result = superAdminService.updateTablePreferences(request);
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "updateTablePreferences");
            detail.put("payload", request);
            operationLogService.log(operator, OperationType.CONFIG_UPDATE, "TABLE_PREFERENCES", null, null, detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @PostMapping("/email/test")
    public ResponseEntity<?> sendTestEmail(@RequestHeader("Authorization") String authorization,
                                           HttpServletRequest requestContext,
                                           @RequestBody(required = false) Map<String, Object> request) {
        String recipient = request == null ? null : (request.get("recipient") == null ? null : String.valueOf(request.get("recipient")));
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result = superAdminService.sendTestEmail(recipient);
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "sendTestEmail");
            detail.put("recipient", recipient);
            operationLogService.log(operator, OperationType.UPDATE, "EMAIL_CONFIG", null, recipient, detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @PostMapping("/sms/test")
    public ResponseEntity<?> sendTestSms(@RequestHeader("Authorization") String authorization,
                                         HttpServletRequest requestContext,
                                         @RequestBody(required = false) Map<String, Object> request) {
        String phone = request == null ? null : (request.get("phone") == null ? null : String.valueOf(request.get("phone")));
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result = superAdminService.sendTestSmsCode(phone, requestContext.getRemoteAddr());
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "sendTestSms");
            detail.put("phone", phone);
            operationLogService.log(operator, OperationType.UPDATE, "SMS_CONFIG", null, phone, detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @PostMapping("/email/send")
    public ResponseEntity<?> sendEmail(@RequestHeader("Authorization") String authorization,
                                       HttpServletRequest requestContext,
                                       @RequestBody SendEmailRequest request) {
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result = superAdminService.sendCustomEmail(
                request == null ? null : request.getRecipient(),
                request == null ? null : request.getSubject(),
                request == null ? null : request.getContent(),
                request == null ? null : request.getHtml()
            );
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "sendCustomEmail");
            detail.put("recipient", request == null ? null : request.getRecipient());
            detail.put("subject", request == null ? null : request.getSubject());
            detail.put("html", request != null && Boolean.TRUE.equals(request.getHtml()));
            operationLogService.log(operator, OperationType.UPDATE, "EMAIL_SEND", null, request == null ? null : request.getRecipient(), detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @GetMapping("/email/templates")
    public ResponseEntity<?> emailTemplates(@RequestHeader("Authorization") String authorization) {
        return handle(authorization, superAdminService::listEmailTemplates);
    }

    @PostMapping("/email/templates/preview")
    public ResponseEntity<?> previewEmailTemplate(@RequestHeader("Authorization") String authorization,
                                                  @RequestBody(required = false) SendEmailRequest request) {
        return handle(authorization, () -> superAdminService.previewEmailTemplate(
            request == null ? null : request.getTemplateKey(),
            request == null ? null : request.getVariables()
        ));
    }

    @PostMapping("/email/templates/send")
    public ResponseEntity<?> sendEmailTemplate(@RequestHeader("Authorization") String authorization,
                                               HttpServletRequest requestContext,
                                               @RequestBody(required = false) SendEmailRequest request) {
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result = superAdminService.sendTemplateEmail(
                request == null ? null : request.getRecipient(),
                request == null ? null : request.getTemplateKey(),
                request == null ? null : request.getVariables()
            );
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "sendTemplateEmail");
            detail.put("recipient", request == null ? null : request.getRecipient());
            detail.put("templateKey", request == null ? null : request.getTemplateKey());
            operationLogService.log(operator, OperationType.UPDATE, "EMAIL_SEND", null, request == null ? null : request.getRecipient(), detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @GetMapping("/vip-orders/{orderId}/payment-preview")
    public ResponseEntity<?> previewVipOrderPayment(@RequestHeader("Authorization") String authorization,
                                                    @PathVariable Long orderId) {
        return handle(authorization, () -> superAdminService.previewVipOrderPayment(orderId));
    }

    @PostMapping("/vip-orders/{orderId}/payment-initiate")
    public ResponseEntity<?> initiateVipOrderPayment(@RequestHeader("Authorization") String authorization,
                                                     HttpServletRequest requestContext,
                                                     @PathVariable Long orderId) {
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result = superAdminService.initiateVipOrderPayment(orderId);
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "paymentInitiate");
            detail.put("orderId", orderId);
            operationLogService.log(operator, OperationType.UPDATE, "VIP_ORDER", orderId, null, detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @GetMapping("/vip-orders/{orderId}/refund-preview")
    public ResponseEntity<?> previewVipOrderRefund(@RequestHeader("Authorization") String authorization,
                                                   @PathVariable Long orderId,
                                                   @RequestParam(required = false) Integer refundAmountFen) {
        return handle(authorization, () -> superAdminService.previewVipOrderRefund(orderId, refundAmountFen));
    }

    @PostMapping("/payments/notify-preview/{providerType}")
    public ResponseEntity<?> previewPaymentNotify(@RequestHeader("Authorization") String authorization,
                                                  @PathVariable String providerType,
                                                  @RequestBody(required = false) String rawBody,
                                                  @RequestParam Map<String, String> requestParams,
                                                  @RequestHeader Map<String, String> headers,
                                                  HttpServletRequest request) {
        return handle(authorization, () -> superAdminService.previewPaymentNotify(
            providerType,
            mergeNotifyPayload(resolveBodyPayload(rawBody, request), requestParams, headers, rawBody),
            headers
        ));
    }

    @PostMapping("/vip-orders/{orderId}/mock-pay")
    public ResponseEntity<?> mockPayVipOrder(@RequestHeader("Authorization") String authorization,
                                             HttpServletRequest request,
                                             @PathVariable Long orderId) {
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result = superAdminService.mockPayVipOrder(orderId);
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "mockPay");
            detail.put("orderId", orderId);
            operationLogService.log(operator, OperationType.UPDATE, "VIP_ORDER", orderId, null, detail, request.getRemoteAddr());
            return result;
        });
    }

    @PostMapping("/vip-orders/{orderId}/cancel")
    public ResponseEntity<?> cancelVipOrder(@RequestHeader("Authorization") String authorization,
                                            HttpServletRequest requestContext,
                                            @PathVariable Long orderId,
                                            @RequestBody(required = false) Map<String, Object> request) {
        String remark = request == null || request.get("remark") == null ? null : String.valueOf(request.get("remark"));
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result = superAdminService.cancelVipOrder(orderId, remark);
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "cancel");
            detail.put("orderId", orderId);
            detail.put("remark", remark);
            operationLogService.log(operator, OperationType.UPDATE, "VIP_ORDER", orderId, null, detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @PostMapping("/vip-orders/{orderId}/refund")
    public ResponseEntity<?> refundVipOrder(@RequestHeader("Authorization") String authorization,
                                            HttpServletRequest requestContext,
                                            @PathVariable Long orderId,
                                            @RequestBody(required = false) Map<String, Object> request) {
        Integer parsedRefundAmountFen = null;
        if (request != null && request.get("refundAmountFen") != null) {
            parsedRefundAmountFen = Integer.parseInt(String.valueOf(request.get("refundAmountFen")));
        }
        final Integer refundAmountFen = parsedRefundAmountFen;
        String remark = request == null || request.get("remark") == null ? null : String.valueOf(request.get("remark"));
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result = superAdminService.refundVipOrder(orderId, refundAmountFen, remark);
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "refund");
            detail.put("orderId", orderId);
            detail.put("refundAmountFen", refundAmountFen);
            detail.put("remark", remark);
            operationLogService.log(operator, OperationType.UPDATE, "VIP_ORDER", orderId, null, detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @PostMapping("/vip-orders/{orderId}/refund-confirm")
    public ResponseEntity<?> confirmVipOrderRefund(@RequestHeader("Authorization") String authorization,
                                                   HttpServletRequest requestContext,
                                                   @PathVariable Long orderId,
                                                   @RequestBody(required = false) Map<String, Object> request) {
        Integer parsedRefundAmountFen = null;
        if (request != null && request.get("refundAmountFen") != null) {
            parsedRefundAmountFen = Integer.parseInt(String.valueOf(request.get("refundAmountFen")));
        }
        final Integer refundAmountFen = parsedRefundAmountFen;
        String remark = request == null || request.get("remark") == null ? null : String.valueOf(request.get("remark"));
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result = superAdminService.confirmVipOrderRefundSuccess(orderId, refundAmountFen, remark);
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "refundConfirmSuccess");
            detail.put("orderId", orderId);
            detail.put("refundAmountFen", refundAmountFen);
            detail.put("remark", remark);
            operationLogService.log(operator, OperationType.UPDATE, "VIP_ORDER", orderId, null, detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @PostMapping("/vip-orders/{orderId}/refund-failed")
    public ResponseEntity<?> markVipOrderRefundFailed(@RequestHeader("Authorization") String authorization,
                                                      HttpServletRequest requestContext,
                                                      @PathVariable Long orderId,
                                                      @RequestBody(required = false) Map<String, Object> request) {
        String remark = request == null || request.get("remark") == null ? null : String.valueOf(request.get("remark"));
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result = superAdminService.markVipOrderRefundFailed(orderId, remark);
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "refundConfirmFailed");
            detail.put("orderId", orderId);
            detail.put("remark", remark);
            operationLogService.log(operator, OperationType.UPDATE, "VIP_ORDER", orderId, null, detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @GetMapping("/users")
    public ResponseEntity<?> users(@RequestHeader("Authorization") String authorization,
                                   @RequestParam(required = false) Integer page,
                                   @RequestParam(required = false) Integer size,
                                   @RequestParam(required = false) String keyword) {
        return handle(authorization, () -> superAdminService.listUsers(page, size, keyword));
    }

    @GetMapping("/login-records")
    public ResponseEntity<?> loginRecords(@RequestHeader("Authorization") String authorization,
                                          @RequestParam(required = false) Long userId,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size) {
        return handle(authorization, () -> superAdminService.listLoginRecords(userId, page, size));
    }

    @GetMapping("/operation-logs")
    public ResponseEntity<?> operationLogs(@RequestHeader("Authorization") String authorization,
                                           @RequestParam(required = false) Long userId,
                                           @RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size) {
        return handle(authorization, () -> superAdminService.listOperationLogs(userId, page, size));
    }

    @GetMapping("/vip-plans")
    public ResponseEntity<?> vipPlans(@RequestHeader("Authorization") String authorization) {
        return handle(authorization, superAdminService::listVipPlans);
    }

    @PostMapping("/vip-plans")
    public ResponseEntity<?> createVipPlan(@RequestHeader("Authorization") String authorization,
                                           HttpServletRequest requestContext,
                                           @RequestBody Map<String, Object> request) {
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result = superAdminService.createVipPlan(request);
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "createVipPlan");
            detail.put("payload", request);
            operationLogService.log(operator, OperationType.UPDATE, "VIP_PLAN", extractLong(result, "id"), null, detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @PutMapping("/vip-plans/{planId}")
    public ResponseEntity<?> updateVipPlan(@RequestHeader("Authorization") String authorization,
                                           HttpServletRequest requestContext,
                                           @PathVariable Long planId,
                                           @RequestBody Map<String, Object> request) {
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result = superAdminService.updateVipPlan(planId, request);
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "updateVipPlan");
            detail.put("planId", planId);
            detail.put("payload", request);
            operationLogService.log(operator, OperationType.UPDATE, "VIP_PLAN", planId, null, detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @GetMapping("/vip-orders")
    public ResponseEntity<?> vipOrders(@RequestHeader("Authorization") String authorization,
                                       @RequestParam(required = false) Long userId,
                                       @RequestParam(required = false) Boolean autoRenewEnabled,
                                       @RequestParam(required = false) Boolean dueForRenewal,
                                       @RequestParam(required = false) Integer page,
                                       @RequestParam(required = false) Integer size) {
        return handle(authorization, () -> superAdminService.listVipOrders(userId, page, size, autoRenewEnabled, dueForRenewal));
    }

    @GetMapping("/vip-orders/by-order-no")
    public ResponseEntity<?> vipOrderByOrderNo(@RequestHeader("Authorization") String authorization,
                                               @RequestParam String orderNo) {
        return handle(authorization, () -> superAdminService.getVipOrderByOrderNo(orderNo));
    }

    @GetMapping("/vip-orders/renewal-preview")
    public ResponseEntity<?> vipOrderRenewalPreview(@RequestHeader("Authorization") String authorization,
                                                    @RequestParam(required = false) Integer limit) {
        return handle(authorization, () -> superAdminService.previewVipRenewals(limit));
    }

    @PostMapping("/vip-orders/renewal-execute")
    public ResponseEntity<?> vipOrderRenewalExecute(@RequestHeader("Authorization") String authorization,
                                                    HttpServletRequest requestContext,
                                                    @RequestBody(required = false) Map<String, Object> request) {
        Integer limit = null;
        if (request != null && request.get("limit") != null) {
            limit = Integer.parseInt(String.valueOf(request.get("limit")));
        }
        final Integer safeLimit = limit;
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result = superAdminService.executeVipRenewals(safeLimit);
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "renewalExecute");
            detail.put("limit", safeLimit);
            operationLogService.log(operator, OperationType.UPDATE, "VIP_ORDER_RENEWAL", null, null, detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @PostMapping("/vip-orders")
    public ResponseEntity<?> createVipOrder(@RequestHeader("Authorization") String authorization,
                                            HttpServletRequest requestContext,
                                            @RequestBody Map<String, Object> request) {
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result = superAdminService.createVipOrder(request);
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "create");
            detail.put("payload", request);
            operationLogService.log(operator, OperationType.UPDATE, "VIP_ORDER", null, null, detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @PutMapping("/vip-orders/{orderId}")
    public ResponseEntity<?> updateVipOrder(@RequestHeader("Authorization") String authorization,
                                            HttpServletRequest requestContext,
                                            @PathVariable Long orderId,
                                            @RequestBody Map<String, Object> request) {
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result = superAdminService.updateVipOrder(orderId, request);
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "update");
            detail.put("orderId", orderId);
            detail.put("payload", request);
            operationLogService.log(operator, OperationType.UPDATE, "VIP_ORDER", orderId, null, detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @PutMapping("/users/{userId}")
    public ResponseEntity<?> updateUser(@RequestHeader("Authorization") String authorization,
                                        HttpServletRequest requestContext,
                                        @PathVariable Long userId,
                                        @RequestBody Map<String, Object> request) {
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result = superAdminService.updateUser(userId, request);
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "updateUser");
            detail.put("userId", userId);
            detail.put("payload", request);
            operationLogService.log(operator, OperationType.UPDATE, "USER_ACCOUNT", userId, null, detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @PostMapping("/users/{userId}/password")
    public ResponseEntity<?> resetUserPassword(@RequestHeader("Authorization") String authorization,
                                               HttpServletRequest requestContext,
                                               @PathVariable Long userId,
                                               @RequestBody Map<String, Object> request) {
        String newPassword = request == null || request.get("newPassword") == null
            ? null
            : String.valueOf(request.get("newPassword"));
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result = superAdminService.resetUserPassword(userId, newPassword);
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "resetUserPassword");
            detail.put("userId", userId);
            operationLogService.log(operator, OperationType.UPDATE, "USER_PASSWORD", userId, null, detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @GetMapping("/storage-providers")
    public ResponseEntity<?> storageProviders(@RequestHeader("Authorization") String authorization) {
        return handle(authorization, () -> Map.of("storageProviders", superAdminService.listStorageProviders()));
    }

    @PostMapping("/storage-providers")
    public ResponseEntity<?> createStorageProvider(@RequestHeader("Authorization") String authorization,
                                                   HttpServletRequest requestContext,
                                                   @RequestBody Map<String, Object> request) {
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result = superAdminService.createStorageProvider(request);
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "createStorageProvider");
            detail.put("payload", request);
            operationLogService.log(operator, OperationType.UPDATE, "STORAGE_PROVIDER", extractLong(result, "id"), null, detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @PutMapping("/storage-providers/{providerId}")
    public ResponseEntity<?> updateStorageProvider(@RequestHeader("Authorization") String authorization,
                                                   HttpServletRequest requestContext,
                                                   @PathVariable Long providerId,
                                                   @RequestBody Map<String, Object> request) {
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result = superAdminService.updateStorageProvider(providerId, request);
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "updateStorageProvider");
            detail.put("providerId", providerId);
            detail.put("payload", request);
            operationLogService.log(operator, OperationType.UPDATE, "STORAGE_PROVIDER", providerId, null, detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @PostMapping("/storage-providers/test")
    public ResponseEntity<?> testStorageProvider(@RequestHeader("Authorization") String authorization,
                                                 HttpServletRequest requestContext,
                                                 @RequestBody Map<String, Object> request) {
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result = superAdminService.testStorageProvider(request);
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "testStorageProvider");
            detail.put("payload", request);
            operationLogService.log(operator, OperationType.UPDATE, "STORAGE_PROVIDER_TEST", null, null, detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @PostMapping("/legacy-migration/run")
    public ResponseEntity<?> runLegacyMigration(@RequestHeader("Authorization") String authorization,
                                                HttpServletRequest requestContext) {
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result = superAdminService.runLegacyDataMigration();
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "runLegacyMigration");
            operationLogService.log(operator, OperationType.UPDATE, "LEGACY_MIGRATION", null, null, detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @PostMapping("/storage-migration/preview")
    public ResponseEntity<?> previewStorageMigration(@RequestHeader("Authorization") String authorization,
                                                     @RequestBody(required = false) Map<String, Object> request) {
        return handle(authorization, () -> superAdminService.previewStorageMigration(request == null ? Map.of() : request));
    }

    @PostMapping("/storage-migration/execute")
    public ResponseEntity<?> executeStorageMigration(@RequestHeader("Authorization") String authorization,
                                                     HttpServletRequest requestContext,
                                                     @RequestBody(required = false) Map<String, Object> request) {
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result;
            try {
                result = superAdminService.executeStorageMigration(request == null ? Map.of() : request);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "executeStorageMigration");
            detail.put("payload", request);
            operationLogService.log(operator, OperationType.UPDATE, "STORAGE_MIGRATION", null, null, detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @PostMapping("/storage-cleanup/preview")
    public ResponseEntity<?> previewStorageCleanup(@RequestHeader("Authorization") String authorization,
                                                   @RequestBody(required = false) Map<String, Object> request) {
        return handle(authorization, () -> superAdminService.previewStorageCleanup(request == null ? Map.of() : request));
    }

    @PostMapping("/storage-cleanup/execute")
    public ResponseEntity<?> executeStorageCleanup(@RequestHeader("Authorization") String authorization,
                                                   HttpServletRequest requestContext,
                                                   @RequestBody(required = false) Map<String, Object> request) {
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result;
            try {
                result = superAdminService.executeStorageCleanup(request == null ? Map.of() : request);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "executeStorageCleanup");
            detail.put("payload", request);
            operationLogService.log(operator, OperationType.DELETE, "STORAGE_CLEANUP", null, null, detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @GetMapping("/models")
    public ResponseEntity<?> models(@RequestHeader("Authorization") String authorization) {
        return handle(authorization, () -> Map.of("models", modelManagementService.listModels()));
    }

    @PostMapping("/models/{modelKey}/download")
    public ResponseEntity<?> downloadModel(@RequestHeader("Authorization") String authorization,
                                           HttpServletRequest requestContext,
                                           @PathVariable String modelKey,
                                           @RequestBody Map<String, Object> request) {
        String url = request == null || request.get("url") == null ? null : String.valueOf(request.get("url"));
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result = modelManagementService.downloadModel(modelKey, url);
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "downloadModel");
            detail.put("modelKey", modelKey);
            detail.put("url", url);
            operationLogService.log(operator, OperationType.UPDATE, "MODEL_FILE", null, modelKey, detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @PostMapping("/models/{modelKey}/reload")
    public ResponseEntity<?> reloadModel(@RequestHeader("Authorization") String authorization,
                                         HttpServletRequest requestContext,
                                         @PathVariable String modelKey) {
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result = modelManagementService.reloadModel(modelKey);
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "reloadModel");
            detail.put("modelKey", modelKey);
            operationLogService.log(operator, OperationType.UPDATE, "MODEL_RUNTIME", null, modelKey, detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @PostMapping("/models/{modelKey}/rebuild")
    public ResponseEntity<?> rebuildModel(@RequestHeader("Authorization") String authorization,
                                          HttpServletRequest requestContext,
                                          @PathVariable String modelKey,
                                          @RequestBody(required = false) Map<String, Object> request) {
        boolean includeMissingItems = request != null && Boolean.parseBoolean(String.valueOf(request.getOrDefault("includeMissingItems", false)));
        boolean forceRebuild = request != null && Boolean.parseBoolean(String.valueOf(request.getOrDefault("forceRebuild", false)));
        return handle(authorization, () -> {
            UserAccount operator = authService.getCurrentUserEntity(extractBearerToken(authorization));
            Object result = modelManagementService.triggerRebuild(modelKey, includeMissingItems, forceRebuild);
            Map<String, Object> detail = new HashMap<>();
            detail.put("action", "rebuildModel");
            detail.put("modelKey", modelKey);
            detail.put("includeMissingItems", includeMissingItems);
            detail.put("forceRebuild", forceRebuild);
            operationLogService.log(operator, OperationType.UPDATE, "MODEL_REBUILD", null, modelKey, detail, requestContext.getRemoteAddr());
            return result;
        });
    }

    @GetMapping("/model-tasks/{taskId}")
    public ResponseEntity<?> modelTask(@RequestHeader("Authorization") String authorization,
                                       @PathVariable String taskId) {
        return handle(authorization, () -> modelManagementService.getTask(taskId));
    }

    private ResponseEntity<?> handle(String authorization, SuperAdminSupplier supplier) {
        try {
            authService.requireSuperAdmin(extractBearerToken(authorization));
            return ResponseEntity.ok(supplier.get());
        } catch (RuntimeException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", sanitizeErrorMessage(e.getMessage(), "操作失败"));
            int status = "仅超级管理员可执行此操作".equals(e.getMessage()) || "Token无效".equals(e.getMessage()) ? 403 : 400;
            return ResponseEntity.status(status).body(error);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", sanitizeErrorMessage(e.getMessage(), "操作失败")));
        }
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new RuntimeException("Token无效");
        }
        return authorization.substring(7);
    }

    private Long extractLong(Object source, String key) {
        if (!(source instanceof Map)) {
            return null;
        }
        Object value = ((Map<?, ?>) source).get(key);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @FunctionalInterface
    private interface SuperAdminSupplier {
        Object get();
    }

    private String sanitizeErrorMessage(String message, String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }
        Matcher matcher = EMBEDDED_PATH_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        boolean replaced = false;
        while (matcher.find()) {
            String candidate = matcher.group(1);
            String sanitizedCandidate = userPathService.toDisplayPath(candidate, true);
            if (!candidate.equals(sanitizedCandidate)) {
                replaced = true;
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(sanitizedCandidate));
        }
        matcher.appendTail(buffer);
        return replaced ? buffer.toString() : message;
    }

    private Map<String, Object> mergeNotifyPayload(Map<String, Object> payload,
                                                   Map<String, String> requestParams,
                                                   Map<String, String> headers,
                                                   String rawBody) {
        Map<String, Object> mergedPayload = new LinkedHashMap<>();
        if (requestParams != null && !requestParams.isEmpty()) {
            mergedPayload.putAll(requestParams);
        }
        if (payload != null && !payload.isEmpty()) {
            mergedPayload.putAll(payload);
        }
        if (headers != null && !headers.isEmpty()) {
            mergedPayload.put("_headers", new LinkedHashMap<>(headers));
        }
        if (rawBody != null && !rawBody.isBlank()) {
            mergedPayload.put("rawBody", rawBody);
        }
        return mergedPayload;
    }

    private Map<String, Object> resolveBodyPayload(String rawBody, HttpServletRequest request) {
        return PaymentPayloadParser.resolveBodyPayload(rawBody, request, objectMapper);
    }
}
