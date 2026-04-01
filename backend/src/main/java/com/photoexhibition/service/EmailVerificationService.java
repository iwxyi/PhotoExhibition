package com.photoexhibition.service;

import com.photoexhibition.entity.EmailCodePurpose;
import com.photoexhibition.entity.EmailVerificationCode;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.repository.EmailVerificationCodeRepository;
import com.photoexhibition.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationCodeRepository emailVerificationCodeRepository;
    private final UserAccountRepository userAccountRepository;
    private final EmailConfigService emailConfigService;
    private final EmailSenderService emailSenderService;

    @Transactional
    public Map<String, Object> sendLoginCode(String email, String requestIp) {
        EmailConfigService.EmailResolvedSettings settings = emailConfigService.getResolvedSettings();
        if (!settings.isCodeLoginEnabled()) {
            throw new RuntimeException("当前系统未开启邮箱验证码登录");
        }

        String normalizedEmail = normalizeEmail(email);
        UserAccount user = userAccountRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> new RuntimeException("该邮箱未绑定账号"));

        validateSendRateLimit(normalizedEmail, requestIp);
        emailVerificationCodeRepository.invalidateActiveCodes(normalizedEmail, EmailCodePurpose.LOGIN, LocalDateTime.now(), "新验证码已发送");

        String code = generateCode();
        EmailDispatchResult dispatchResult = dispatchCodeEmail(normalizedEmail, code, settings, "登录验证码", "登录");

        EmailVerificationCode record = new EmailVerificationCode();
        record.setUserId(user.getId());
        record.setEmail(normalizedEmail);
        record.setPurpose(EmailCodePurpose.LOGIN);
        record.setVerificationCode(code);
        record.setRequestIp(requestIp);
        record.setSuccess(dispatchResult.isSuccess());
        record.setProviderMessageId(dispatchResult.getProviderMessageId());
        record.setProviderResponse(dispatchResult.getProviderResponse());
        record.setExpiresAt(LocalDateTime.now().plusMinutes(settings.getCodeExpireMinutes()));
        emailVerificationCodeRepository.save(record);

        return buildResult("邮箱验证码已发送", settings.getCodeExpireMinutes(), dispatchResult.getDebugCode());
    }

    @Transactional
    public Map<String, Object> sendEmailVerifyCode(UserAccount user, String requestIp) {
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        String normalizedEmail = normalizeEmail(user.getEmail());
        EmailConfigService.EmailResolvedSettings settings = emailConfigService.getResolvedSettings();
        validateSendRateLimit(normalizedEmail, requestIp);
        emailVerificationCodeRepository.invalidateActiveCodes(normalizedEmail, EmailCodePurpose.EMAIL_VERIFY, LocalDateTime.now(), "新验证码已发送");

        String code = generateCode();
        EmailDispatchResult dispatchResult = dispatchCodeEmail(normalizedEmail, code, settings, "邮箱验证", "邮箱绑定");

        EmailVerificationCode record = new EmailVerificationCode();
        record.setUserId(user.getId());
        record.setEmail(normalizedEmail);
        record.setPurpose(EmailCodePurpose.EMAIL_VERIFY);
        record.setVerificationCode(code);
        record.setRequestIp(requestIp);
        record.setSuccess(dispatchResult.isSuccess());
        record.setProviderMessageId(dispatchResult.getProviderMessageId());
        record.setProviderResponse(dispatchResult.getProviderResponse());
        record.setExpiresAt(LocalDateTime.now().plusMinutes(settings.getCodeExpireMinutes()));
        emailVerificationCodeRepository.save(record);

        return buildResult("邮箱验证码已发送", settings.getCodeExpireMinutes(), dispatchResult.getDebugCode());
    }

    @Transactional
    public Map<String, Object> sendPasswordResetCode(String email, String requestIp) {
        String normalizedEmail = normalizeEmail(email);
        UserAccount user = userAccountRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> new RuntimeException("该邮箱未绑定账号"));
        EmailConfigService.EmailResolvedSettings settings = emailConfigService.getResolvedSettings();
        validateSendRateLimit(normalizedEmail, requestIp);
        emailVerificationCodeRepository.invalidateActiveCodes(normalizedEmail, EmailCodePurpose.PASSWORD_RESET, LocalDateTime.now(), "新验证码已发送");

        String code = generateCode();
        EmailDispatchResult dispatchResult = dispatchCodeEmail(normalizedEmail, code, settings, "重置密码验证码", "重置密码");

        EmailVerificationCode record = new EmailVerificationCode();
        record.setUserId(user.getId());
        record.setEmail(normalizedEmail);
        record.setPurpose(EmailCodePurpose.PASSWORD_RESET);
        record.setVerificationCode(code);
        record.setRequestIp(requestIp);
        record.setSuccess(dispatchResult.isSuccess());
        record.setProviderMessageId(dispatchResult.getProviderMessageId());
        record.setProviderResponse(dispatchResult.getProviderResponse());
        record.setExpiresAt(LocalDateTime.now().plusMinutes(settings.getCodeExpireMinutes()));
        emailVerificationCodeRepository.save(record);

        return buildResult("重置密码验证码已发送", settings.getCodeExpireMinutes(), dispatchResult.getDebugCode());
    }

    @Transactional
    public UserAccount verifyLoginCode(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        EmailVerificationCode verificationCode = findAvailableCode(normalizedEmail, EmailCodePurpose.LOGIN, code);
        UserAccount user = userAccountRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> new RuntimeException("该邮箱未绑定账号"));
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            user.setEmailVerified(true);
            userAccountRepository.save(user);
        }
        consumeCode(verificationCode);
        return user;
    }

    @Transactional
    public UserAccount verifyEmailCode(UserAccount user, String code) {
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        String normalizedEmail = normalizeEmail(user.getEmail());
        EmailVerificationCode verificationCode = findAvailableCode(normalizedEmail, EmailCodePurpose.EMAIL_VERIFY, code);
        if (!user.getId().equals(verificationCode.getUserId())) {
            throw new RuntimeException("验证码与当前账号不匹配");
        }
        user.setEmailVerified(true);
        userAccountRepository.save(user);
        consumeCode(verificationCode);
        return user;
    }

    @Transactional
    public UserAccount verifyPasswordResetCode(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        EmailVerificationCode verificationCode = findAvailableCode(normalizedEmail, EmailCodePurpose.PASSWORD_RESET, code);
        UserAccount user = userAccountRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> new RuntimeException("该邮箱未绑定账号"));
        consumeCode(verificationCode);
        return user;
    }

    private EmailVerificationCode findAvailableCode(String email, EmailCodePurpose purpose, String code) {
        if (code == null || !code.trim().matches("^\\d{4,8}$")) {
            throw new RuntimeException("验证码格式不正确");
        }
        EmailVerificationCode latestCode = emailVerificationCodeRepository
            .findTopByEmailAndPurposeAndSuccessTrueAndUsedFalseOrderByCreatedAtDesc(email, purpose)
            .orElseThrow(() -> new RuntimeException("验证码不存在或已失效"));
        if (latestCode.getExpiresAt() == null || latestCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            latestCode.setUsed(true);
            latestCode.setUsedAt(LocalDateTime.now());
            latestCode.setFailureReason("验证码已过期");
            emailVerificationCodeRepository.save(latestCode);
            throw new RuntimeException("验证码已过期");
        }
        if (!latestCode.getVerificationCode().equals(code.trim())) {
            throw new RuntimeException("验证码错误");
        }
        return latestCode;
    }

    private void consumeCode(EmailVerificationCode code) {
        code.setUsed(true);
        code.setUsedAt(LocalDateTime.now());
        code.setFailureReason(null);
        emailVerificationCodeRepository.save(code);
    }

    private void validateSendRateLimit(String email, String requestIp) {
        LocalDateTime now = LocalDateTime.now();
        if (emailVerificationCodeRepository.countByEmailAndCreatedAtAfter(email, now.minusMinutes(1)) >= 1) {
            throw new RuntimeException("验证码发送过于频繁，请稍后再试");
        }
        if (emailVerificationCodeRepository.countByEmailAndCreatedAtAfter(email, now.minusDays(1)) >= 20) {
            throw new RuntimeException("该邮箱今日验证码发送次数已达上限");
        }
        if (requestIp != null && !requestIp.isBlank()) {
            if (emailVerificationCodeRepository.countByRequestIpAndCreatedAtAfter(requestIp, now.minusMinutes(1)) >= 5) {
                throw new RuntimeException("当前IP发送过于频繁，请稍后再试");
            }
            if (emailVerificationCodeRepository.countByRequestIpAndCreatedAtAfter(requestIp, now.minusDays(1)) >= 50) {
                throw new RuntimeException("当前IP今日发送次数已达上限");
            }
        }
    }

    private EmailDispatchResult dispatchCodeEmail(String email,
                                                  String code,
                                                  EmailConfigService.EmailResolvedSettings settings,
                                                  String subjectLabel,
                                                  String actionLabel) {
        if (settings.isMockEnabled()) {
            return new EmailDispatchResult(true, "MOCK", "mock-mode", code);
        }
        EmailSenderService.EmailSendResult sendResult = emailSenderService.sendEmail(
            email,
            "Photo Exhibition " + subjectLabel,
            buildMessageContent(code, settings.getCodeExpireMinutes(), actionLabel),
            false
        );
        return new EmailDispatchResult(sendResult.isSuccess(), sendResult.getProviderType(), sendResult.getMessage(), null);
    }

    private String buildMessageContent(String code, int expireMinutes, String actionLabel) {
        return "您正在进行 Photo Exhibition 的" + actionLabel + "操作。\n\n"
            + "验证码：" + code + "\n"
            + "有效期：" + expireMinutes + " 分钟\n\n"
            + "若非本人操作，请忽略本邮件。";
    }

    private Map<String, Object> buildResult(String message, int expireMinutes, String debugCode) {
        Map<String, Object> result = new HashMap<>();
        result.put("message", message);
        result.put("expiresInSeconds", expireMinutes * 60);
        if (debugCode != null) {
            result.put("debugCode", debugCode);
        }
        return result;
    }

    private String generateCode() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
    }

    private String normalizeEmail(String email) {
        String normalized = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        if (normalized == null || normalized.isBlank() || !normalized.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new RuntimeException("邮箱格式不正确");
        }
        return normalized;
    }

    private static class EmailDispatchResult {
        private final boolean success;
        private final String providerMessageId;
        private final String providerResponse;
        private final String debugCode;

        private EmailDispatchResult(boolean success, String providerMessageId, String providerResponse, String debugCode) {
            this.success = success;
            this.providerMessageId = providerMessageId;
            this.providerResponse = providerResponse;
            this.debugCode = debugCode;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getProviderMessageId() {
            return providerMessageId;
        }

        public String getProviderResponse() {
            return providerResponse;
        }

        public String getDebugCode() {
            return debugCode;
        }
    }
}
