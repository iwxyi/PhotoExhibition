package com.photoexhibition.controller;

import com.photoexhibition.dto.LoginRequest;
import com.photoexhibition.dto.LoginResponse;
import com.photoexhibition.dto.CurrentUserResponse;
import com.photoexhibition.dto.RegisterRequest;
import com.photoexhibition.dto.ResetPasswordByEmailRequest;
import com.photoexhibition.dto.ResetPasswordByPhoneRequest;
import com.photoexhibition.dto.SendEmailCodeRequest;
import com.photoexhibition.dto.SendSmsCodeRequest;
import com.photoexhibition.dto.UpdateProfileRequest;
import com.photoexhibition.dto.VerifyEmailCodeRequest;
import com.photoexhibition.dto.VerifySmsCodeRequest;
import com.photoexhibition.service.AuthService;
import com.photoexhibition.service.OperationLogService;
import com.photoexhibition.service.UserPathService;
import com.photoexhibition.entity.OperationType;
import com.photoexhibition.entity.UserAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Pattern EMBEDDED_PATH_PATTERN =
        Pattern.compile("(storage://[^\\s,;]+|[A-Za-z]:\\\\[^\\s,;]+|/(?:[^\\s,;])+)");

    private final AuthService authService;
    private final OperationLogService operationLogService;
    private final UserPathService userPathService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        try {
            LoginResponse response = authService.login(
                request,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
            );
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                .body(java.util.Map.of("error", sanitizeErrorMessage(e.getMessage(), "登录失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(java.util.Map.of("error", "登录失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    @GetMapping("/validate")
    public ResponseEntity<Boolean> validate(@RequestHeader("Authorization") String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.ok(false);
        }
        token = token.substring(7);
        boolean valid = authService.validateToken(token);
        return ResponseEntity.ok(valid);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        try {
            LoginResponse response = authService.register(request);
            UserAccount user = resolveAuthenticatedUser(response.getToken());
            operationLogService.log(
                user,
                OperationType.UPDATE,
                "USER_ACCOUNT",
                response.getUserId(),
                null,
                java.util.Map.of(
                    "action", "REGISTER",
                    "username", response.getUsername(),
                    "slug", response.getSlug(),
                    "phoneBound", response.getPhone() != null && !response.getPhone().isBlank()
                ),
                httpRequest.getRemoteAddr()
            );
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                .body(java.util.Map.of("error", sanitizeErrorMessage(e.getMessage(), "注册失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(java.util.Map.of("error", "注册失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    @PostMapping("/send-code")
    public ResponseEntity<?> sendLoginCode(@RequestBody SendSmsCodeRequest request, HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(authService.sendLoginCode(request.getPhone(), httpRequest.getRemoteAddr()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                .body(java.util.Map.of("error", sanitizeErrorMessage(e.getMessage(), "发送验证码失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(java.util.Map.of("error", "发送验证码失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    @PostMapping("/email/send-code")
    public ResponseEntity<?> sendEmailLoginCode(@RequestBody SendEmailCodeRequest request, HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(authService.sendEmailLoginCode(request.getEmail(), httpRequest.getRemoteAddr()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                .body(java.util.Map.of("error", sanitizeErrorMessage(e.getMessage(), "发送验证码失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(java.util.Map.of("error", "发送验证码失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    @PostMapping("/password-reset/phone/send-code")
    public ResponseEntity<?> sendPasswordResetPhoneCode(@RequestBody SendSmsCodeRequest request, HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(authService.sendPasswordResetPhoneCode(request.getPhone(), httpRequest.getRemoteAddr()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                .body(java.util.Map.of("error", sanitizeErrorMessage(e.getMessage(), "发送重置密码验证码失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(java.util.Map.of("error", "发送重置密码验证码失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    @PostMapping("/password-reset/email/send-code")
    public ResponseEntity<?> sendPasswordResetEmailCode(@RequestBody SendEmailCodeRequest request, HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(authService.sendPasswordResetEmailCode(request.getEmail(), httpRequest.getRemoteAddr()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                .body(java.util.Map.of("error", sanitizeErrorMessage(e.getMessage(), "发送重置密码验证码失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(java.util.Map.of("error", "发送重置密码验证码失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    @GetMapping("/public-settings")
    public ResponseEntity<?> publicSettings() {
        return ResponseEntity.ok(authService.getPublicAuthSettings());
    }

    @GetMapping("/public-user")
    public ResponseEntity<?> publicUser(@RequestParam String userSlug) {
        try {
            return ResponseEntity.ok(authService.getPublicUserProfile(userSlug));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", sanitizeErrorMessage(e.getMessage(), "获取公开用户信息失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", "获取公开用户信息失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    @GetMapping("/public-users")
    public ResponseEntity<?> publicUsers() {
        try {
            return ResponseEntity.ok(java.util.Map.of("users", authService.listPublicUsers()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", sanitizeErrorMessage(e.getMessage(), "获取公开用户列表失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", "获取公开用户列表失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader("Authorization") String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "未登录"));
        }
        token = token.substring(7);
        try {
            return ResponseEntity.ok(authService.getCurrentUser(token));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", sanitizeErrorMessage(e.getMessage(), "获取当前用户失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", "获取当前用户失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    @GetMapping("/vip/overview")
    public ResponseEntity<?> vipOverview(@RequestHeader("Authorization") String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "未登录"));
        }
        token = token.substring(7);
        try {
            return ResponseEntity.ok(authService.getVipOverview(token));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", sanitizeErrorMessage(e.getMessage(), "获取会员信息失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", "获取会员信息失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    @GetMapping("/vip/plans")
    public ResponseEntity<?> vipPlans() {
        try {
            return ResponseEntity.ok(authService.listVipPlans());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", sanitizeErrorMessage(e.getMessage(), "获取会员套餐失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", "获取会员套餐失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    @GetMapping("/vip/orders")
    public ResponseEntity<?> vipOrders(@RequestHeader("Authorization") String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "未登录"));
        }
        token = token.substring(7);
        try {
            return ResponseEntity.ok(authService.listMyVipOrders(token));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", sanitizeErrorMessage(e.getMessage(), "获取会员订单失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", "获取会员订单失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    @PostMapping("/vip/orders")
    public ResponseEntity<?> createVipOrder(@RequestHeader("Authorization") String token,
                                            @RequestBody java.util.Map<String, Object> request) {
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "未登录"));
        }
        token = token.substring(7);
        try {
            Object planIdValue = request == null ? null : request.get("planId");
            Long planId = planIdValue == null ? null : Long.parseLong(String.valueOf(planIdValue));
            java.util.Map<String, Object> result = authService.createVipOrder(token, planId);
            UserAccount user = authService.getCurrentUserEntity(token);
            operationLogService.log(
                user,
                OperationType.UPDATE,
                "VIP_ORDER",
                parseLong(result.get("id")),
                null,
                java.util.Map.of(
                    "action", "CREATE_VIP_ORDER",
                    "planId", planId,
                    "orderNo", result.get("orderNo"),
                    "status", result.get("status")
                ),
                null
            );
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", sanitizeErrorMessage(e.getMessage(), "创建会员订单失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", "创建会员订单失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    @GetMapping("/vip/orders/{orderId}/checkout")
    public ResponseEntity<?> previewVipCheckout(@RequestHeader("Authorization") String token,
                                                @PathVariable Long orderId) {
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "未登录"));
        }
        token = token.substring(7);
        try {
            return ResponseEntity.ok(authService.previewVipCheckout(token, orderId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", sanitizeErrorMessage(e.getMessage(), "获取结算信息失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", "获取结算信息失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    @PostMapping("/vip/orders/{orderId}/checkout/initiate")
    public ResponseEntity<?> initiateVipCheckout(@RequestHeader("Authorization") String token,
                                                 @PathVariable Long orderId,
                                                 HttpServletRequest httpRequest) {
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "未登录"));
        }
        token = token.substring(7);
        try {
            java.util.Map<String, Object> result = authService.initiateVipCheckout(token, orderId);
            UserAccount user = authService.getCurrentUserEntity(token);
            operationLogService.log(
                user,
                OperationType.UPDATE,
                "VIP_ORDER",
                orderId,
                null,
                java.util.Map.of(
                    "action", "INITIATE_VIP_CHECKOUT",
                    "orderId", orderId,
                    "providerType", result.get("providerType"),
                    "actionType", result.get("actionType"),
                    "redirect", result.get("redirect")
                ),
                httpRequest.getRemoteAddr()
            );
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", sanitizeErrorMessage(e.getMessage(), "发起支付失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", "发起支付失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    @PostMapping("/vip/orders/{orderId}/mock-pay")
    public ResponseEntity<?> mockPayVipOrder(@RequestHeader("Authorization") String token,
                                             @PathVariable Long orderId,
                                             HttpServletRequest httpRequest) {
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "未登录"));
        }
        token = token.substring(7);
        try {
            java.util.Map<String, Object> result = authService.mockPayVipOrder(token, orderId);
            UserAccount user = authService.getCurrentUserEntity(token);
            operationLogService.log(
                user,
                OperationType.UPDATE,
                "VIP_ORDER",
                orderId,
                null,
                java.util.Map.of(
                    "action", "MOCK_PAY_VIP_ORDER",
                    "orderId", orderId,
                    "status", result.get("status"),
                    "message", result.get("message")
                ),
                httpRequest.getRemoteAddr()
            );
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", sanitizeErrorMessage(e.getMessage(), "模拟支付失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", "模拟支付失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    @PutMapping("/vip/orders/{orderId}/auto-renew")
    public ResponseEntity<?> updateVipOrderAutoRenew(@RequestHeader("Authorization") String token,
                                                     @PathVariable Long orderId,
                                                     @RequestBody(required = false) java.util.Map<String, Object> request,
                                                     HttpServletRequest httpRequest) {
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "未登录"));
        }
        token = token.substring(7);
        try {
            boolean autoRenewEnabled = request != null && Boolean.parseBoolean(String.valueOf(request.get("autoRenewEnabled")));
            java.util.Map<String, Object> result = authService.updateVipOrderAutoRenew(token, orderId, autoRenewEnabled);
            UserAccount user = authService.getCurrentUserEntity(token);
            operationLogService.log(
                user,
                OperationType.UPDATE,
                "VIP_ORDER",
                orderId,
                null,
                java.util.Map.of(
                    "action", "UPDATE_VIP_ORDER_AUTO_RENEW",
                    "orderId", orderId,
                    "autoRenewEnabled", autoRenewEnabled,
                    "nextRenewalAt", result.get("nextRenewalAt")
                ),
                httpRequest.getRemoteAddr()
            );
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", sanitizeErrorMessage(e.getMessage(), "更新自动续费失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", "更新自动续费失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestHeader("Authorization") String token,
                                           @RequestBody UpdateProfileRequest request,
                                           HttpServletRequest httpRequest) {
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "未登录"));
        }
        token = token.substring(7);
        try {
            CurrentUserResponse response = authService.updateProfile(token, request);
            UserAccount user = authService.getCurrentUserEntity(token);
            operationLogService.log(
                user,
                OperationType.UPDATE,
                "USER_PROFILE",
                response.getUserId(),
                response.getSlug(),
                java.util.Map.of(
                    "action", "UPDATE_PROFILE",
                    "slug", response.getSlug(),
                    "nickname", response.getNickname(),
                    "phone", response.getPhone(),
                    "projectNameZh", response.getProjectNameZh(),
                    "projectNameEn", response.getProjectNameEn()
                ),
                httpRequest.getRemoteAddr()
            );
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", sanitizeErrorMessage(e.getMessage(), "更新资料失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", "更新资料失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    @PostMapping("/phone/send-code")
    public ResponseEntity<?> sendPhoneVerifyCode(@RequestHeader("Authorization") String token,
                                                 HttpServletRequest httpRequest) {
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "未登录"));
        }
        token = token.substring(7);
        try {
            java.util.Map<String, Object> result = authService.sendPhoneVerificationCode(token, httpRequest.getRemoteAddr());
            UserAccount user = authService.getCurrentUserEntity(token);
            operationLogService.log(
                user,
                OperationType.UPDATE,
                "PHONE_VERIFY",
                user.getId(),
                user.getPhone(),
                java.util.Map.of(
                    "action", "SEND_PHONE_VERIFY_CODE",
                    "phone", user.getPhone(),
                    "provider", result.get("provider")
                ),
                httpRequest.getRemoteAddr()
            );
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", sanitizeErrorMessage(e.getMessage(), "发送手机号验证码失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", "发送手机号验证码失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    @PostMapping("/email/verify/send-code")
    public ResponseEntity<?> sendEmailVerifyCode(@RequestHeader("Authorization") String token,
                                                 HttpServletRequest httpRequest) {
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "未登录"));
        }
        token = token.substring(7);
        try {
            java.util.Map<String, Object> result = authService.sendEmailVerificationCode(token, httpRequest.getRemoteAddr());
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", sanitizeErrorMessage(e.getMessage(), "发送邮箱验证码失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", "发送邮箱验证码失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    @PostMapping("/phone/verify")
    public ResponseEntity<?> verifyPhone(@RequestHeader("Authorization") String token,
                                         @RequestBody VerifySmsCodeRequest request,
                                         HttpServletRequest httpRequest) {
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "未登录"));
        }
        token = token.substring(7);
        try {
            CurrentUserResponse response = authService.verifyPhone(token, request.getCode());
            UserAccount user = authService.getCurrentUserEntity(token);
            operationLogService.log(
                user,
                OperationType.UPDATE,
                "PHONE_VERIFY",
                response.getUserId(),
                response.getPhone(),
                java.util.Map.of(
                    "action", "VERIFY_PHONE",
                    "phone", response.getPhone(),
                    "phoneVerified", response.getPhoneVerified()
                ),
                httpRequest.getRemoteAddr()
            );
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", sanitizeErrorMessage(e.getMessage(), "验证手机号失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", "验证手机号失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    @PostMapping("/email/verify")
    public ResponseEntity<?> verifyEmail(@RequestHeader("Authorization") String token,
                                         @RequestBody VerifyEmailCodeRequest request) {
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "未登录"));
        }
        token = token.substring(7);
        try {
            return ResponseEntity.ok(authService.verifyEmail(token, request.getCode()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", sanitizeErrorMessage(e.getMessage(), "验证邮箱失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", "验证邮箱失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    @PostMapping("/password-reset/phone/confirm")
    public ResponseEntity<?> resetPasswordByPhone(@RequestBody ResetPasswordByPhoneRequest request) {
        try {
            authService.resetPasswordByPhone(request.getPhone(), request.getCode(), request.getNewPassword(), request.getConfirmPassword());
            return ResponseEntity.ok(java.util.Map.of("message", "密码已重置，请使用新密码登录"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", sanitizeErrorMessage(e.getMessage(), "重置密码失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", "重置密码失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    @PostMapping("/password-reset/email/confirm")
    public ResponseEntity<?> resetPasswordByEmail(@RequestBody ResetPasswordByEmailRequest request) {
        try {
            authService.resetPasswordByEmail(request.getEmail(), request.getCode(), request.getNewPassword(), request.getConfirmPassword());
            return ResponseEntity.ok(java.util.Map.of("message", "密码已重置，请使用新密码登录"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", sanitizeErrorMessage(e.getMessage(), "重置密码失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", "重置密码失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    @PostMapping("/avatar")
    public ResponseEntity<?> uploadAvatar(@RequestHeader("Authorization") String token,
                                          @RequestParam("file") MultipartFile file,
                                          HttpServletRequest httpRequest) {
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "未登录"));
        }
        token = token.substring(7);
        try {
            CurrentUserResponse response = authService.uploadAvatar(token, file);
            UserAccount user = authService.getCurrentUserEntity(token);
            operationLogService.log(
                user,
                OperationType.UPLOAD,
                "USER_AVATAR",
                response.getUserId(),
                response.getAvatarPath(),
                java.util.Map.of(
                    "action", "UPLOAD_AVATAR",
                    "fileName", file != null ? file.getOriginalFilename() : null,
                    "size", file != null ? file.getSize() : null
                ),
                httpRequest.getRemoteAddr()
            );
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", sanitizeErrorMessage(e.getMessage(), "上传头像失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", "上传头像失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    private UserAccount resolveAuthenticatedUser(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return authService.getCurrentUserEntity(token);
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
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
}
