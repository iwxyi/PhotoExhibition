package com.photoexhibition.service;

import com.photoexhibition.entity.SmsCodePurpose;
import com.photoexhibition.entity.SmsVerificationCode;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.repository.SmsVerificationCodeRepository;
import com.photoexhibition.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class SmsVerificationService {

    private final SmsVerificationCodeRepository smsVerificationCodeRepository;
    private final UserAccountRepository userAccountRepository;
    private final SmsConfigService smsConfigService;
    private final SmsSenderService smsSenderService;

    @Transactional
    public Map<String, Object> sendLoginCode(String phone, String requestIp) {
        String normalizedPhone = normalizePhone(phone);
        UserAccount user = userAccountRepository.findByPhone(normalizedPhone)
            .orElseThrow(() -> new RuntimeException("该手机号未绑定账号"));

        validateSendRateLimit(normalizedPhone, requestIp);
        smsVerificationCodeRepository.invalidateActiveCodes(normalizedPhone, SmsCodePurpose.LOGIN, LocalDateTime.now(), "新验证码已发送");

        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        SmsSenderService.SmsSendResult sendResult = smsSenderService.sendLoginCode(normalizedPhone, code);

        SmsVerificationCode record = new SmsVerificationCode();
        record.setUserId(user.getId());
        record.setPhone(normalizedPhone);
        record.setPurpose(SmsCodePurpose.LOGIN);
        record.setVerificationCode(code);
        record.setRequestIp(requestIp);
        record.setSuccess(sendResult.isSuccess());
        record.setProviderMessageId(sendResult.getProviderMessageId());
        record.setProviderResponse(sendResult.getRawResponse());
        record.setExpiresAt(LocalDateTime.now().plusMinutes(smsConfigService.getResolvedSettings().getCodeExpireMinutes()));
        smsVerificationCodeRepository.save(record);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "验证码已发送");
        result.put("expiresInSeconds", smsConfigService.getResolvedSettings().getCodeExpireMinutes() * 60);
        if (sendResult.getDebugCode() != null) {
            result.put("debugCode", sendResult.getDebugCode());
        }
        return result;
    }

    @Transactional
    public Map<String, Object> sendPasswordResetCode(String phone, String requestIp) {
        String normalizedPhone = normalizePhone(phone);
        UserAccount user = userAccountRepository.findByPhone(normalizedPhone)
            .orElseThrow(() -> new RuntimeException("该手机号未绑定账号"));

        validateSendRateLimit(normalizedPhone, requestIp);
        smsVerificationCodeRepository.invalidateActiveCodes(normalizedPhone, SmsCodePurpose.PASSWORD_RESET, LocalDateTime.now(), "新验证码已发送");

        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        SmsSenderService.SmsSendResult sendResult = smsSenderService.sendLoginCode(normalizedPhone, code);

        SmsVerificationCode record = new SmsVerificationCode();
        record.setUserId(user.getId());
        record.setPhone(normalizedPhone);
        record.setPurpose(SmsCodePurpose.PASSWORD_RESET);
        record.setVerificationCode(code);
        record.setRequestIp(requestIp);
        record.setSuccess(sendResult.isSuccess());
        record.setProviderMessageId(sendResult.getProviderMessageId());
        record.setProviderResponse(sendResult.getRawResponse());
        record.setExpiresAt(LocalDateTime.now().plusMinutes(smsConfigService.getResolvedSettings().getCodeExpireMinutes()));
        smsVerificationCodeRepository.save(record);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "重置密码验证码已发送");
        result.put("expiresInSeconds", smsConfigService.getResolvedSettings().getCodeExpireMinutes() * 60);
        if (sendResult.getDebugCode() != null) {
            result.put("debugCode", sendResult.getDebugCode());
        }
        return result;
    }

    @Transactional
    public Map<String, Object> sendPhoneVerifyCode(UserAccount user, String requestIp) {
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        String normalizedPhone = normalizePhone(user.getPhone());
        validateSendRateLimit(normalizedPhone, requestIp);
        smsVerificationCodeRepository.invalidateActiveCodes(normalizedPhone, SmsCodePurpose.PHONE_VERIFY, LocalDateTime.now(), "新验证码已发送");

        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        SmsSenderService.SmsSendResult sendResult = smsSenderService.sendLoginCode(normalizedPhone, code);

        SmsVerificationCode record = new SmsVerificationCode();
        record.setUserId(user.getId());
        record.setPhone(normalizedPhone);
        record.setPurpose(SmsCodePurpose.PHONE_VERIFY);
        record.setVerificationCode(code);
        record.setRequestIp(requestIp);
        record.setSuccess(sendResult.isSuccess());
        record.setProviderMessageId(sendResult.getProviderMessageId());
        record.setProviderResponse(sendResult.getRawResponse());
        record.setExpiresAt(LocalDateTime.now().plusMinutes(smsConfigService.getResolvedSettings().getCodeExpireMinutes()));
        smsVerificationCodeRepository.save(record);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "手机号验证码已发送");
        result.put("expiresInSeconds", smsConfigService.getResolvedSettings().getCodeExpireMinutes() * 60);
        if (sendResult.getDebugCode() != null) {
            result.put("debugCode", sendResult.getDebugCode());
        }
        return result;
    }

    @Transactional
    public UserAccount verifyLoginCode(String phone, String code) {
        String normalizedPhone = normalizePhone(phone);
        if (code == null || !code.matches("^\\d{4,8}$")) {
            throw new RuntimeException("验证码格式不正确");
        }

        SmsVerificationCode latestCode = smsVerificationCodeRepository
            .findTopByPhoneAndPurposeAndSuccessTrueAndUsedFalseOrderByCreatedAtDesc(normalizedPhone, SmsCodePurpose.LOGIN)
            .orElseThrow(() -> new RuntimeException("验证码不存在或已失效"));

        if (latestCode.getExpiresAt() == null || latestCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            latestCode.setUsed(true);
            latestCode.setUsedAt(LocalDateTime.now());
            latestCode.setFailureReason("验证码已过期");
            smsVerificationCodeRepository.save(latestCode);
            throw new RuntimeException("验证码已过期");
        }
        if (!latestCode.getVerificationCode().equals(code.trim())) {
            throw new RuntimeException("验证码错误");
        }

        latestCode.setUsed(true);
        latestCode.setUsedAt(LocalDateTime.now());
        latestCode.setFailureReason(null);
        smsVerificationCodeRepository.save(latestCode);

        UserAccount user = userAccountRepository.findByPhone(normalizedPhone)
            .orElseThrow(() -> new RuntimeException("该手机号未绑定账号"));
        if (!Boolean.TRUE.equals(user.getPhoneVerified())) {
            user.setPhoneVerified(true);
            userAccountRepository.save(user);
        }
        return user;
    }

    @Transactional
    public UserAccount verifyPhoneCode(UserAccount user, String code) {
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        String normalizedPhone = normalizePhone(user.getPhone());
        if (code == null || !code.matches("^\\d{4,8}$")) {
            throw new RuntimeException("验证码格式不正确");
        }

        SmsVerificationCode latestCode = smsVerificationCodeRepository
            .findTopByPhoneAndPurposeAndSuccessTrueAndUsedFalseOrderByCreatedAtDesc(normalizedPhone, SmsCodePurpose.PHONE_VERIFY)
            .orElseThrow(() -> new RuntimeException("验证码不存在或已失效"));

        if (!user.getId().equals(latestCode.getUserId())) {
            throw new RuntimeException("验证码与当前账号不匹配");
        }
        if (latestCode.getExpiresAt() == null || latestCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            latestCode.setUsed(true);
            latestCode.setUsedAt(LocalDateTime.now());
            latestCode.setFailureReason("验证码已过期");
            smsVerificationCodeRepository.save(latestCode);
            throw new RuntimeException("验证码已过期");
        }
        if (!latestCode.getVerificationCode().equals(code.trim())) {
            throw new RuntimeException("验证码错误");
        }

        latestCode.setUsed(true);
        latestCode.setUsedAt(LocalDateTime.now());
        latestCode.setFailureReason(null);
        smsVerificationCodeRepository.save(latestCode);

        user.setPhoneVerified(true);
        userAccountRepository.save(user);
        return user;
    }

    @Transactional
    public UserAccount verifyPasswordResetCode(String phone, String code) {
        String normalizedPhone = normalizePhone(phone);
        if (code == null || !code.matches("^\\d{4,8}$")) {
            throw new RuntimeException("验证码格式不正确");
        }

        SmsVerificationCode latestCode = smsVerificationCodeRepository
            .findTopByPhoneAndPurposeAndSuccessTrueAndUsedFalseOrderByCreatedAtDesc(normalizedPhone, SmsCodePurpose.PASSWORD_RESET)
            .orElseThrow(() -> new RuntimeException("验证码不存在或已失效"));

        if (latestCode.getExpiresAt() == null || latestCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            latestCode.setUsed(true);
            latestCode.setUsedAt(LocalDateTime.now());
            latestCode.setFailureReason("验证码已过期");
            smsVerificationCodeRepository.save(latestCode);
            throw new RuntimeException("验证码已过期");
        }
        if (!latestCode.getVerificationCode().equals(code.trim())) {
            throw new RuntimeException("验证码错误");
        }

        latestCode.setUsed(true);
        latestCode.setUsedAt(LocalDateTime.now());
        latestCode.setFailureReason(null);
        smsVerificationCodeRepository.save(latestCode);

        return userAccountRepository.findByPhone(normalizedPhone)
            .orElseThrow(() -> new RuntimeException("该手机号未绑定账号"));
    }

    private void validateSendRateLimit(String phone, String requestIp) {
        LocalDateTime now = LocalDateTime.now();
        if (smsVerificationCodeRepository.countByPhoneAndCreatedAtAfter(phone, now.minusMinutes(1)) >= 1) {
            throw new RuntimeException("验证码发送过于频繁，请稍后再试");
        }
        if (smsVerificationCodeRepository.countByPhoneAndCreatedAtAfter(phone, now.minusDays(1)) >= 20) {
            throw new RuntimeException("该手机号今日验证码发送次数已达上限");
        }
        if (requestIp != null && !requestIp.isBlank()) {
            if (smsVerificationCodeRepository.countByRequestIpAndCreatedAtAfter(requestIp, now.minusMinutes(1)) >= 5) {
                throw new RuntimeException("当前IP发送过于频繁，请稍后再试");
            }
            if (smsVerificationCodeRepository.countByRequestIpAndCreatedAtAfter(requestIp, now.minusDays(1)) >= 50) {
                throw new RuntimeException("当前IP今日发送次数已达上限");
            }
        }
    }

    private String normalizePhone(String phone) {
        String normalized = phone == null ? null : phone.trim();
        if (normalized == null || !normalized.matches("^1\\d{10}$")) {
            throw new RuntimeException("手机号格式不正确");
        }
        return normalized;
    }
}
