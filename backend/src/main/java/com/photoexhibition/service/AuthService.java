package com.photoexhibition.service;

import com.photoexhibition.config.JwtConfig;
import com.photoexhibition.dto.CurrentUserResponse;
import com.photoexhibition.dto.LoginRequest;
import com.photoexhibition.dto.LoginResponse;
import com.photoexhibition.dto.PublicUserProfileResponse;
import com.photoexhibition.dto.RegisterRequest;
import com.photoexhibition.dto.UpdateProfileRequest;
import com.photoexhibition.dto.VerifySmsCodeRequest;
import com.photoexhibition.entity.AdminUser;
import com.photoexhibition.entity.LoginMethod;
import com.photoexhibition.entity.LoginRecord;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserRole;
import com.photoexhibition.entity.UserStatus;
import com.photoexhibition.repository.AdminUserRepository;
import com.photoexhibition.repository.LoginRecordRepository;
import com.photoexhibition.repository.UserAccountRepository;
import com.photoexhibition.repository.VipPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    private final AdminUserRepository adminUserRepository;
    private final UserAccountRepository userAccountRepository;
    private final LoginRecordRepository loginRecordRepository;
    private final JwtConfig jwtConfig;
    private final SystemConfigService systemConfigService;
    private final SmsVerificationService smsVerificationService;
    private final SmsConfigService smsConfigService;
    private final EmailVerificationService emailVerificationService;
    private final EmailConfigService emailConfigService;
    private final UserPathService userPathService;
    private final StorageProviderService storageProviderService;
    private final VipPlanRepository vipPlanRepository;
    private final UserStorageService userStorageService;
    private final UserVipService userVipService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
        if (isSmsCodeLogin(request)) {
            return loginWithSmsCode(request, ipAddress, userAgent);
        }
        if (isEmailCodeLogin(request)) {
            return loginWithEmailCode(request, ipAddress, userAgent);
        }

        String identifier = normalize(request.getUsername());
        if (identifier == null || identifier.isEmpty()) {
            throw new RuntimeException("用户名、手机号或邮箱不能为空");
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new RuntimeException("密码不能为空");
        }
        log.info("登录: {}", identifier);

        UserAccount user = findUserAccountByIdentifier(identifier).orElse(null);
        if (user != null) {
            return loginWithUserAccount(user, request.getPassword(), identifier, ipAddress, userAgent);
        }

        return loginWithLegacyAdmin(request, identifier, ipAddress, userAgent);
    }

    public java.util.Map<String, Object> sendLoginCode(String phone, String ipAddress) {
        return smsVerificationService.sendLoginCode(phone, ipAddress);
    }

    public java.util.Map<String, Object> sendEmailLoginCode(String email, String ipAddress) {
        return emailVerificationService.sendLoginCode(email, ipAddress);
    }

    public java.util.Map<String, Object> sendPasswordResetPhoneCode(String phone, String ipAddress) {
        return smsVerificationService.sendPasswordResetCode(phone, ipAddress);
    }

    public java.util.Map<String, Object> sendPasswordResetEmailCode(String email, String ipAddress) {
        return emailVerificationService.sendPasswordResetCode(email, ipAddress);
    }

    public java.util.Map<String, Object> getPublicAuthSettings() {
        SmsConfigService.SmsResolvedSettings smsSettings = smsConfigService.getResolvedSettings();
        EmailConfigService.EmailResolvedSettings emailSettings = emailConfigService.getResolvedSettings();
        return java.util.Map.of(
            "multiUserEnabled", systemConfigService.isMultiUserEnabled(),
            "forceBindPhone", systemConfigService.isForceBindPhone(),
            "smsLoginEnabled", smsSettings.isEnabled() || smsSettings.isMockEnabled(),
            "emailCodeLoginEnabled", emailSettings.isCodeLoginEnabled() && (emailSettings.isEnabled() || emailSettings.isMockEnabled())
        );
    }

    public PublicUserProfileResponse getPublicUserProfile(String userSlug) {
        String slug = normalizeNullable(userSlug);
        if (slug == null) {
            throw new RuntimeException("用户标识不能为空");
        }
        UserAccount user = userAccountRepository.findBySlug(slug)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new RuntimeException("用户当前不可公开访问");
        }
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            throw new RuntimeException("超级管理员公开站点不可访问");
        }
        if (systemConfigService.isMultiUserEnabled() && Boolean.FALSE.equals(user.getMultiUserVisible())) {
            throw new RuntimeException("用户未公开");
        }
        return PublicUserProfileResponse.builder()
            .userId(user.getId())
            .slug(user.getSlug())
            .username(user.getUsername())
            .nickname(resolveNickname(user))
            .projectNameZh(user.getProjectNameZh())
            .projectNameEn(user.getProjectNameEn())
            .avatarPath(user.getAvatarPath())
            .build();
    }

    public java.util.List<PublicUserProfileResponse> listPublicUsers() {
        if (!systemConfigService.isMultiUserEnabled()) {
            return java.util.List.of();
        }
        return userAccountRepository.findByRoleAndStatusAndMultiUserVisibleTrueOrderByCreatedAtAsc(UserRole.USER_ADMIN, UserStatus.ACTIVE)
            .stream()
            .map(user -> PublicUserProfileResponse.builder()
                .userId(user.getId())
                .slug(user.getSlug())
                .username(user.getUsername())
                .nickname(resolveNickname(user))
                .projectNameZh(user.getProjectNameZh())
                .projectNameEn(user.getProjectNameEn())
                .avatarPath(user.getAvatarPath())
                .build())
            .collect(java.util.stream.Collectors.toList());
    }

    public boolean validateToken(String token) {
        try {
            String username = jwtConfig.extractUsername(token);
            return jwtConfig.validateToken(token, username);
        } catch (Exception e) {
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        try {
            return jwtConfig.extractUsername(token);
        } catch (Exception e) {
            return null;
        }
    }

    public CurrentUserResponse getCurrentUser(String token) {
        UserAccount user = getCurrentUserEntity(token);
        return buildCurrentUserResponse(user);
    }

    public java.util.Map<String, Object> getVipOverview(String token) {
        return userVipService.getOverview(token);
    }

    public java.util.Map<String, Object> listVipPlans(String token) {
        return userVipService.listPlans(token);
    }

    public java.util.Map<String, Object> listMyVipOrders(String token) {
        return userVipService.listMyOrders(token);
    }

    public java.util.Map<String, Object> createVipOrder(String token, Long planId) {
        if (planId == null) {
            throw new RuntimeException("planId 不能为空");
        }
        return userVipService.createOrder(token, planId);
    }

    public java.util.Map<String, Object> previewVipCheckout(String token, Long orderId) {
        return userVipService.previewCheckout(token, orderId);
    }

    public java.util.Map<String, Object> initiateVipCheckout(String token, Long orderId) {
        return userVipService.initiateCheckout(token, orderId);
    }

    public java.util.Map<String, Object> mockPayVipOrder(String token, Long orderId) {
        return userVipService.mockPayOrder(token, orderId);
    }

    public java.util.Map<String, Object> updateVipOrderAutoRenew(String token, Long orderId, boolean autoRenewEnabled) {
        return userVipService.updateAutoRenew(token, orderId, autoRenewEnabled);
    }

    @Transactional
    public java.util.Map<String, Object> sendPhoneVerificationCode(String token, String ipAddress) {
        UserAccount user = getCurrentUserEntity(token);
        if (user.getPhone() == null || user.getPhone().isBlank()) {
            throw new RuntimeException("请先绑定手机号");
        }
        return smsVerificationService.sendPhoneVerifyCode(user, ipAddress);
    }

    @Transactional
    public java.util.Map<String, Object> sendEmailVerificationCode(String token, String ipAddress) {
        UserAccount user = getCurrentUserEntity(token);
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new RuntimeException("请先绑定邮箱");
        }
        return emailVerificationService.sendEmailVerifyCode(user, ipAddress);
    }

    @Transactional
    public CurrentUserResponse verifyPhone(String token, String code) {
        UserAccount user = getCurrentUserEntity(token);
        smsVerificationService.verifyPhoneCode(user, code);
        return buildCurrentUserResponse(user);
    }

    @Transactional
    public CurrentUserResponse verifyEmail(String token, String code) {
        UserAccount user = getCurrentUserEntity(token);
        emailVerificationService.verifyEmailCode(user, code);
        return buildCurrentUserResponse(user);
    }

    @Transactional
    public CurrentUserResponse updateProfile(String token, UpdateProfileRequest request) {
        UserAccount user = getCurrentUserEntity(token);
        String requestedSlug = normalizeNullable(request.getSlug());
        String phone = normalizeNullable(request.getPhone());
        String email = normalizeEmail(request.getEmail());
        if (phone != null && !phone.matches("^1\\d{10}$")) {
            throw new RuntimeException("手机号格式不正确");
        }
        if (email != null && !isValidEmail(email)) {
            throw new RuntimeException("邮箱格式不正确");
        }
        if (phone != null) {
            userAccountRepository.findByPhone(phone)
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> {
                    throw new RuntimeException("手机号已被其他用户使用");
                });
        }
        if (email != null) {
            userAccountRepository.findByEmail(email)
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> {
                    throw new RuntimeException("邮箱已被其他用户使用");
                });
        }
        if (requestedSlug != null) {
            validateProfileSlug(requestedSlug, user.getId());
        }

        String previousPhone = user.getPhone();
        String previousEmail = user.getEmail();
        if (requestedSlug != null) {
            user.setSlug(requestedSlug);
        }
        user.setNickname(normalizeNullable(request.getNickname()));
        user.setPhone(phone);
        user.setEmail(email);
        user.setProjectNameZh(normalizeNullable(request.getProjectNameZh()));
        user.setProjectNameEn(normalizeNullable(request.getProjectNameEn()));
        if (!java.util.Objects.equals(previousPhone, phone)) {
            user.setPhoneVerified(false);
        }
        if (!java.util.Objects.equals(previousEmail, email)) {
            user.setEmailVerified(false);
        }
        userAccountRepository.save(user);
        return getCurrentUser(token);
    }

    @Transactional
    public CurrentUserResponse uploadAvatar(String token, MultipartFile file) {
        UserAccount user = getCurrentUserEntity(token);
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("请选择头像文件");
        }
        if (file.getSize() > 5L * 1024 * 1024) {
            throw new RuntimeException("头像文件不能超过 5MB");
        }

        String extension = resolveAvatarExtension(file);
        Path userRoot = userPathService.getScopedUserDataRoot(user);
        Path avatarPath = userRoot.resolve("avatar" + extension).normalize();

        try {
            Files.createDirectories(userRoot);
            try (var stream = Files.list(userRoot)) {
                stream.filter(path -> path.getFileName().toString().startsWith("avatar."))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
            }
            Files.copy(file.getInputStream(), avatarPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("保存头像失败: " + userPathService.sanitizeVisibleText(e.getMessage()), e);
        }

        user.setAvatarPath("/api/user-files/" + user.getId() + "/" + avatarPath.getFileName());
        userAccountRepository.save(user);
        return getCurrentUser(token);
    }

    @Transactional(readOnly = true)
    public java.util.List<java.util.Map<String, Object>> listRecentLoginRecords(UserAccount user) {
        if (user == null || user.getId() == null) {
            return java.util.List.of();
        }
        return loginRecordRepository.findTop50ByUserIdOrderByCreatedAtDesc(user.getId()).stream()
            .map(record -> {
                java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
                item.put("id", record.getId());
                item.put("userId", record.getUserId());
                item.put("usernameSnapshot", record.getUsernameSnapshot());
                item.put("phoneSnapshot", record.getPhoneSnapshot());
                item.put("loginMethod", record.getLoginMethod() != null ? record.getLoginMethod().name() : null);
                item.put("success", Boolean.TRUE.equals(record.getSuccess()));
                item.put("ipAddress", record.getIpAddress());
                item.put("userAgent", record.getUserAgent());
                item.put("failureReason", record.getFailureReason());
                item.put("createdAt", record.getCreatedAt());
                return item;
            })
            .collect(java.util.stream.Collectors.toList());
    }

    public UserAccount getCurrentUserEntity(String token) {
        String username = getUsernameFromToken(token);
        if (username == null) {
            throw new RuntimeException("Token无效");
        }

        return userAccountRepository.findByUsername(username)
            .orElseGet(() -> migrateLegacyAdminIfExists(username).orElseThrow(() -> new RuntimeException("用户不存在")));
    }

    public UserAccount requireSuperAdmin(String token) {
        UserAccount user = getCurrentUserEntity(token);
        if (user.getRole() != UserRole.SUPER_ADMIN) {
            throw new RuntimeException("仅超级管理员可执行此操作");
        }
        return user;
    }

    @Transactional
    public UserAccount saveUserAccount(UserAccount user) {
        return userAccountRepository.save(user);
    }

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (!systemConfigService.isMultiUserEnabled()) {
            throw new RuntimeException("系统未开启多用户注册");
        }

        String username = normalize(request.getUsername());
        String password = request.getPassword();
        String confirmPassword = request.getConfirmPassword();
        String phone = normalizeNullable(request.getPhone());
        String email = normalizeEmail(request.getEmail());

        if (!password.equals(confirmPassword)) {
            throw new RuntimeException("两次输入的密码不一致");
        }

        validateUsername(username);
        if (systemConfigService.isForceBindPhone() && (phone == null || phone.isBlank())) {
            throw new RuntimeException("当前系统要求注册时必须绑定手机号");
        }
        if (phone != null && !phone.matches("^1\\d{10}$")) {
            throw new RuntimeException("手机号格式不正确");
        }
        if (email != null && !isValidEmail(email)) {
            throw new RuntimeException("邮箱格式不正确");
        }
        if (userAccountRepository.existsByUsername(username)) {
            throw new RuntimeException("用户名已被使用");
        }
        if (phone != null && userAccountRepository.existsByPhone(phone)) {
            throw new RuntimeException("手机号已被使用");
        }
        if (email != null && userAccountRepository.existsByEmail(email)) {
            throw new RuntimeException("邮箱已被使用");
        }

        String slug = generateAvailableSlug(request.getSlug(), username);
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setSlug(slug);
        user.setPhone(phone);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(normalizeNullable(request.getNickname()));
        user.setRole(UserRole.USER_ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        user.setStorageQuotaBytes(systemConfigService.getDefaultUserQuotaBytes());
        user.setVipExtraQuotaBytes(systemConfigService.getDefaultVipExtraQuotaBytes());
        user.setStorageUsedBytes(0L);
        user.setProjectNameZh(normalizeNullable(request.getNickname()));
        user.setProjectNameEn(username);
        userAccountRepository.save(user);

        return buildLoginResponse(user, "注册成功");
    }

    @Transactional
    public LoginResponse changeUsername(String currentUsername, String newUsername, String password) {
        log.info("用户名更改请求: {} -> {}", currentUsername, newUsername);

        String normalizedCurrent = normalize(currentUsername);
        String normalizedNew = normalize(newUsername);
        validateUsername(normalizedNew);

        UserAccount user = userAccountRepository.findByUsername(normalizedCurrent)
            .orElseGet(() -> migrateLegacyAdminIfExists(normalizedCurrent)
                .orElseThrow(() -> new RuntimeException("用户不存在")));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("密码验证失败");
        }
        if (!user.getUsername().equals(normalizedNew) && userAccountRepository.existsByUsername(normalizedNew)) {
            throw new RuntimeException("用户名已被使用");
        }

        String oldUsername = user.getUsername();
        user.setUsername(normalizedNew);
        if (user.getSlug() == null || user.getSlug().equals(oldUsername)) {
            user.setSlug(generateAvailableSlug(normalizedNew, normalizedNew, user.getId()));
        }
        userAccountRepository.save(user);

        syncLegacyAdminUsername(oldUsername, normalizedNew);
        log.info("用户名更改成功: {} -> {}", oldUsername, normalizedNew);
        return buildLoginResponse(user, "用户名修改成功，请使用新用户名重新登录");
    }

    @Transactional
    public void changePassword(String username, String oldPassword, String newPassword) {
        String normalizedUsername = normalize(username);
        if (newPassword == null || newPassword.length() < 6) {
            throw new RuntimeException("新密码长度不能少于6位");
        }

        UserAccount user = userAccountRepository.findByUsername(normalizedUsername)
            .orElseGet(() -> migrateLegacyAdminIfExists(normalizedUsername)
                .orElseThrow(() -> new RuntimeException("用户不存在")));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("旧密码错误");
        }

        String encoded = passwordEncoder.encode(newPassword);
        user.setPassword(encoded);
        user.setLoginAttempts(0);
        user.setLockedUntil(null);
        userAccountRepository.save(user);
        syncLegacyAdminPassword(normalizedUsername, encoded);
    }

    @Transactional
    public void resetPasswordByPhone(String phone, String code, String newPassword, String confirmPassword) {
        validateResetPassword(newPassword, confirmPassword);
        UserAccount user = smsVerificationService.verifyPasswordResetCode(phone, code);
        applyResetPassword(user, newPassword);
    }

    @Transactional
    public void resetPasswordByEmail(String email, String code, String newPassword, String confirmPassword) {
        validateResetPassword(newPassword, confirmPassword);
        UserAccount user = emailVerificationService.verifyPasswordResetCode(email, code);
        applyResetPassword(user, newPassword);
    }

    private LoginResponse loginWithUserAccount(UserAccount user, String password, String identifier, String ipAddress, String userAgent) {
        ensureUserCanLogin(user);

        boolean passwordMatches = passwordEncoder.matches(password, user.getPassword());
        if (!passwordMatches) {
            handleFailedLogin(user);
            recordLogin(user, identifier, ipAddress, userAgent, false, detectLoginMethod(identifier), "用户名或密码错误");
            throw new RuntimeException("用户名或密码错误");
        }

        resetLoginAttempts(user, ipAddress);
        recordLogin(user, identifier, ipAddress, userAgent, true, detectLoginMethod(identifier), null);
        log.info("登录成功: {}", user.getUsername());
        return buildLoginResponse(user, "登录成功");
    }

    private LoginResponse loginWithLegacyAdmin(LoginRequest request, String identifier, String ipAddress, String userAgent) {
        AdminUser legacyUser = adminUserRepository.findByUsername(identifier).orElse(null);
        if (legacyUser == null) {
            if (userAccountRepository.count() == 0 && adminUserRepository.count() == 0) {
                return createDefaultSuperAdmin(request, ipAddress, userAgent);
            }
            recordLogin(null, identifier, ipAddress, userAgent, false, detectLoginMethod(identifier), "用户名或密码错误");
            throw new RuntimeException("用户名或密码错误");
        }

        if (!Boolean.TRUE.equals(legacyUser.getEnabled())) {
            recordLogin(null, identifier, ipAddress, userAgent, false, LoginMethod.USERNAME_PASSWORD, "账户已被禁用");
            throw new RuntimeException("账户已被禁用");
        }
        if (isLegacyAccountLocked(legacyUser)) {
            recordLogin(null, identifier, ipAddress, userAgent, false, LoginMethod.USERNAME_PASSWORD, "账户已被锁定");
            throw new RuntimeException("账户已被锁定，请稍后再试");
        }

        if (!passwordEncoder.matches(request.getPassword(), legacyUser.getPassword())) {
            handleFailedLogin(legacyUser);
            recordLogin(null, identifier, ipAddress, userAgent, false, LoginMethod.USERNAME_PASSWORD, "用户名或密码错误");
            throw new RuntimeException("用户名或密码错误");
        }

        resetLoginAttempts(legacyUser);
        UserAccount migratedUser = migrateLegacyAdmin(legacyUser);
        migratedUser.setLastLoginAt(LocalDateTime.now());
        migratedUser.setLastLoginIp(ipAddress);
        userAccountRepository.save(migratedUser);
        recordLogin(migratedUser, identifier, ipAddress, userAgent, true, LoginMethod.USERNAME_PASSWORD, null);
        log.info("旧管理员登录成功并已兼容迁移: {}", migratedUser.getUsername());
        return buildLoginResponse(migratedUser, "登录成功");
    }

    private LoginResponse loginWithSmsCode(LoginRequest request, String ipAddress, String userAgent) {
        String phone = normalize(request.getUsername());
        if (phone == null || phone.isEmpty()) {
            throw new RuntimeException("手机号不能为空");
        }
        String code = normalize(request.getCode());
        if (code == null || code.isEmpty()) {
            throw new RuntimeException("验证码不能为空");
        }

        UserAccount user = userAccountRepository.findByPhone(phone).orElse(null);
        if (user == null) {
            recordLogin(null, phone, ipAddress, userAgent, false, LoginMethod.SMS_CODE, "手机号未绑定账号");
            throw new RuntimeException("该手机号未绑定账号");
        }

        ensureUserCanLogin(user);
        try {
            UserAccount verifiedUser = smsVerificationService.verifyLoginCode(phone, code);
            resetLoginAttempts(verifiedUser, ipAddress);
            recordLogin(verifiedUser, phone, ipAddress, userAgent, true, LoginMethod.SMS_CODE, null);
            return buildLoginResponse(verifiedUser, "登录成功");
        } catch (RuntimeException e) {
            handleFailedLogin(user);
            recordLogin(user, phone, ipAddress, userAgent, false, LoginMethod.SMS_CODE, e.getMessage());
            throw e;
        }
    }

    private LoginResponse loginWithEmailCode(LoginRequest request, String ipAddress, String userAgent) {
        String email = normalizeEmail(request.getUsername());
        if (email == null || email.isEmpty()) {
            throw new RuntimeException("邮箱不能为空");
        }
        String code = normalize(request.getCode());
        if (code == null || code.isEmpty()) {
            throw new RuntimeException("验证码不能为空");
        }

        UserAccount user = userAccountRepository.findByEmail(email).orElse(null);
        if (user == null) {
            recordLogin(null, email, ipAddress, userAgent, false, LoginMethod.EMAIL_CODE, "邮箱未绑定账号");
            throw new RuntimeException("该邮箱未绑定账号");
        }

        ensureUserCanLogin(user);
        try {
            UserAccount verifiedUser = emailVerificationService.verifyLoginCode(email, code);
            resetLoginAttempts(verifiedUser, ipAddress);
            recordLogin(verifiedUser, email, ipAddress, userAgent, true, LoginMethod.EMAIL_CODE, null);
            return buildLoginResponse(verifiedUser, "登录成功");
        } catch (RuntimeException e) {
            handleFailedLogin(user);
            recordLogin(user, email, ipAddress, userAgent, false, LoginMethod.EMAIL_CODE, e.getMessage());
            throw e;
        }
    }

    private void ensureUserCanLogin(UserAccount user) {
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new RuntimeException("账户已被禁用");
        }
        if (user.getStatus() == UserStatus.LOCKED || isUserAccountLocked(user)) {
            throw new RuntimeException("账户已被锁定，请稍后再试");
        }
        if (user.getStatus() == UserStatus.PENDING) {
            throw new RuntimeException("账户尚未激活");
        }
    }

    private Optional<UserAccount> findUserAccountByIdentifier(String identifier) {
        Optional<UserAccount> byUsername = userAccountRepository.findByUsername(identifier);
        if (byUsername.isPresent()) {
            return byUsername;
        }
        if (identifier != null && identifier.matches("^1\\d{10}$")) {
            return userAccountRepository.findByPhone(identifier);
        }
        if (isValidEmail(identifier)) {
            return userAccountRepository.findByEmail(identifier.toLowerCase(Locale.ROOT));
        }
        return Optional.empty();
    }

    private LoginMethod detectLoginMethod(String identifier) {
        if (identifier != null && identifier.matches("^1\\d{10}$")) {
            return LoginMethod.PHONE_PASSWORD;
        }
        if (isValidEmail(identifier)) {
            return LoginMethod.EMAIL_PASSWORD;
        }
        return LoginMethod.USERNAME_PASSWORD;
    }

    private boolean isSmsCodeLogin(LoginRequest request) {
        String loginType = normalize(request.getLoginType());
        return "smsCode".equalsIgnoreCase(loginType) || "sms_code".equalsIgnoreCase(loginType);
    }

    private boolean isEmailCodeLogin(LoginRequest request) {
        String loginType = normalize(request.getLoginType());
        return "emailCode".equalsIgnoreCase(loginType) || "email_code".equalsIgnoreCase(loginType);
    }

    private String normalizeEmail(String email) {
        String normalized = normalizeNullable(email);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    private LoginResponse buildLoginResponse(UserAccount user, String message) {
        com.photoexhibition.entity.VipPlan vipPlan = user.getCurrentVipPlanId() == null
            ? null
            : vipPlanRepository.findById(user.getCurrentVipPlanId()).orElse(null);
        String token = jwtConfig.generateToken(user.getId(), user.getUsername(), user.getRole().name(), user.getSlug());
        return LoginResponse.builder()
            .token(token)
            .userId(user.getId())
            .slug(user.getSlug())
            .username(user.getUsername())
            .phone(user.getPhone())
            .email(user.getEmail())
            .nickname(resolveNickname(user))
            .phoneVerified(Boolean.TRUE.equals(user.getPhoneVerified()))
            .emailVerified(Boolean.TRUE.equals(user.getEmailVerified()))
            .projectNameZh(user.getProjectNameZh())
            .projectNameEn(user.getProjectNameEn())
            .avatarPath(user.getAvatarPath())
            .role(user.getRole().name())
            .multiUserEnabled(systemConfigService.isMultiUserEnabled())
            .currentVipPlanId(user.getCurrentVipPlanId())
            .currentVipPlanName(vipPlan != null ? vipPlan.getName() : null)
            .currentVipPlanCode(vipPlan != null ? vipPlan.getCode() : null)
            .vipExpireAt(user.getVipExpireAt() == null ? null : user.getVipExpireAt().toString())
            .effectiveStorageQuotaBytes(userStorageService.getEffectiveQuotaBytes(user))
            .storageUsedBytes(user.getStorageUsedBytes() == null ? 0L : user.getStorageUsedBytes())
            .message(message)
            .build();
    }

    private CurrentUserResponse buildCurrentUserResponse(UserAccount user) {
        com.photoexhibition.entity.VipPlan vipPlan = user.getCurrentVipPlanId() == null
            ? null
            : vipPlanRepository.findById(user.getCurrentVipPlanId()).orElse(null);
        return CurrentUserResponse.builder()
            .userId(user.getId())
            .slug(user.getSlug())
            .username(user.getUsername())
            .phone(user.getPhone())
            .email(user.getEmail())
            .nickname(resolveNickname(user))
            .phoneVerified(Boolean.TRUE.equals(user.getPhoneVerified()))
            .emailVerified(Boolean.TRUE.equals(user.getEmailVerified()))
            .projectNameZh(user.getProjectNameZh())
            .projectNameEn(user.getProjectNameEn())
            .avatarPath(user.getAvatarPath())
            .role(user.getRole().name())
            .multiUserEnabled(systemConfigService.isMultiUserEnabled())
            .currentVipPlanId(user.getCurrentVipPlanId())
            .currentVipPlanName(vipPlan != null ? vipPlan.getName() : null)
            .currentVipPlanCode(vipPlan != null ? vipPlan.getCode() : null)
            .vipExpireAt(user.getVipExpireAt() == null ? null : user.getVipExpireAt().toString())
            .effectiveStorageQuotaBytes(userStorageService.getEffectiveQuotaBytes(user))
            .storageUsedBytes(user.getStorageUsedBytes() == null ? 0L : user.getStorageUsedBytes())
            .build();
    }

    private String resolveNickname(UserAccount user) {
        if (user.getNickname() != null && !user.getNickname().trim().isEmpty()) {
            return user.getNickname();
        }
        return user.getUsername();
    }

    private boolean isUserAccountLocked(UserAccount user) {
        return user.getLockedUntil() != null && LocalDateTime.now().isBefore(user.getLockedUntil());
    }

    private boolean isLegacyAccountLocked(AdminUser user) {
        return user.getLockedUntil() != null && LocalDateTime.now().isBefore(user.getLockedUntil());
    }

    private void handleFailedLogin(UserAccount user) {
        LocalDateTime now = LocalDateTime.now();
        user.setLoginAttempts(user.getLoginAttempts() + 1);
        if (user.getLoginAttempts() >= MAX_LOGIN_ATTEMPTS) {
            user.setLockedUntil(now.plusMinutes(LOCK_DURATION_MINUTES));
            user.setStatus(UserStatus.LOCKED);
        }
        userAccountRepository.save(user);
    }

    private void handleFailedLogin(AdminUser user) {
        LocalDateTime now = LocalDateTime.now();
        user.setLastLoginAttempt(now);
        user.setLoginAttempts(user.getLoginAttempts() + 1);
        if (user.getLoginAttempts() >= MAX_LOGIN_ATTEMPTS) {
            user.setLockedUntil(now.plusMinutes(LOCK_DURATION_MINUTES));
        }
        adminUserRepository.save(user);
    }

    private void resetLoginAttempts(UserAccount user, String ipAddress) {
        user.setLoginAttempts(0);
        user.setLockedUntil(null);
        if (user.getStatus() == UserStatus.LOCKED) {
            user.setStatus(UserStatus.ACTIVE);
        }
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ipAddress);
        userAccountRepository.save(user);
    }

    private void resetLoginAttempts(AdminUser user) {
        user.setLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAttempt(null);
        adminUserRepository.save(user);
    }

    private void recordLogin(UserAccount user, String identifier, String ipAddress, String userAgent, boolean success,
                             LoginMethod loginMethod, String failureReason) {
        LoginRecord record = new LoginRecord();
        if (user != null) {
            record.setUserId(user.getId());
            record.setUsernameSnapshot(user.getUsername());
            record.setPhoneSnapshot(user.getPhone());
        } else {
            record.setUsernameSnapshot(identifier);
            if (identifier != null && identifier.matches("^1\\d{10}$")) {
                record.setPhoneSnapshot(identifier);
            }
        }
        record.setLoginMethod(loginMethod);
        record.setSuccess(success);
        record.setIpAddress(ipAddress);
        record.setUserAgent(userAgent);
        record.setFailureReason(failureReason);
        loginRecordRepository.save(record);
    }

    @Transactional
    protected Optional<UserAccount> migrateLegacyAdminIfExists(String username) {
        return adminUserRepository.findByUsername(username).map(this::migrateLegacyAdmin);
    }

    private UserAccount migrateLegacyAdmin(AdminUser legacyUser) {
        return userAccountRepository.findByUsername(legacyUser.getUsername()).orElseGet(() -> {
            UserAccount user = new UserAccount();
            user.setUsername(legacyUser.getUsername());
            user.setSlug(generateAvailableSlug(legacyUser.getUsername(), legacyUser.getUsername()));
            user.setPassword(legacyUser.getPassword());
            user.setNickname(legacyUser.getUsername());
            user.setRole(UserRole.SUPER_ADMIN);
            user.setStatus(Boolean.TRUE.equals(legacyUser.getEnabled()) ? UserStatus.ACTIVE : UserStatus.DISABLED);
            user.setLoginAttempts(legacyUser.getLoginAttempts() != null ? legacyUser.getLoginAttempts() : 0);
            user.setLockedUntil(legacyUser.getLockedUntil());
            user.setStorageQuotaBytes(Long.MAX_VALUE);
            user.setStorageUsedBytes(0L);
            user.setProjectNameZh("光忆集");
            user.setProjectNameEn("Photo Exhibition");
            return userAccountRepository.save(user);
        });
    }

    @Transactional
    protected LoginResponse createDefaultSuperAdmin(LoginRequest request, String ipAddress, String userAgent) {
        String defaultUsername = "admin";
        String defaultPassword = "admin";

        if (!defaultUsername.equals(request.getUsername()) || !defaultPassword.equals(request.getPassword())) {
            throw new RuntimeException("系统尚未初始化，请使用默认凭证登录：用户名 admin，密码 admin");
        }

        AdminUser adminUser = new AdminUser();
        adminUser.setUsername(defaultUsername);
        adminUser.setPassword(passwordEncoder.encode(defaultPassword));
        adminUser.setRole("ADMIN");
        adminUser.setEnabled(true);
        adminUser.setLoginAttempts(0);
        adminUserRepository.save(adminUser);

        UserAccount user = new UserAccount();
        user.setUsername(defaultUsername);
        user.setSlug(generateAvailableSlug(defaultUsername, defaultUsername));
        user.setPassword(adminUser.getPassword());
        user.setNickname("管理员");
        user.setRole(UserRole.SUPER_ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        user.setStorageQuotaBytes(Long.MAX_VALUE);
        user.setStorageUsedBytes(0L);
        user.setProjectNameZh("光忆集");
        user.setProjectNameEn("Photo Exhibition");
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ipAddress);
        userAccountRepository.save(user);

        recordLogin(user, defaultUsername, ipAddress, userAgent, true, LoginMethod.USERNAME_PASSWORD, null);
        return buildLoginResponse(user, "系统初始化完成！默认超级管理员账户已创建，请及时修改密码以确保安全");
    }

    private void syncLegacyAdminUsername(String oldUsername, String newUsername) {
        adminUserRepository.findByUsername(oldUsername).ifPresent(adminUser -> {
            adminUser.setUsername(newUsername);
            adminUserRepository.save(adminUser);
        });
    }

    private void syncLegacyAdminPassword(String username, String encodedPassword) {
        adminUserRepository.findByUsername(username).ifPresent(adminUser -> {
            adminUser.setPassword(encodedPassword);
            adminUser.setLoginAttempts(0);
            adminUser.setLockedUntil(null);
            adminUserRepository.save(adminUser);
        });
    }

    private void validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new RuntimeException("用户名不能为空");
        }
        if (username.length() < 3 || username.length() > 50) {
            throw new RuntimeException("用户名长度必须在3-50个字符之间");
        }
        if (!username.matches("^[a-zA-Z0-9_-]+$")) {
            throw new RuntimeException("用户名只能包含字母、数字、下划线和连字符");
        }
    }

    private void validateResetPassword(String newPassword, String confirmPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new RuntimeException("新密码长度不能少于6位");
        }
        if (confirmPassword == null || !newPassword.equals(confirmPassword)) {
            throw new RuntimeException("两次输入的密码不一致");
        }
    }

    private void applyResetPassword(UserAccount user, String newPassword) {
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        String encoded = passwordEncoder.encode(newPassword);
        user.setPassword(encoded);
        user.setLoginAttempts(0);
        user.setLockedUntil(null);
        if (user.getStatus() == UserStatus.LOCKED) {
            user.setStatus(UserStatus.ACTIVE);
        }
        userAccountRepository.save(user);
        syncLegacyAdminPassword(user.getUsername(), encoded);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeNullable(String value) {
        String normalized = normalize(value);
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }

    private String generateAvailableSlug(String requestedSlug, String fallbackSeed) {
        return generateAvailableSlug(requestedSlug, fallbackSeed, null);
    }

    private String generateAvailableSlug(String requestedSlug, String fallbackSeed, Long selfId) {
        String base = slugify(normalizeNullable(requestedSlug));
        if (base == null || base.isEmpty()) {
            base = slugify(fallbackSeed);
        }
        if (base == null || base.isEmpty()) {
            base = "user";
        }

        String candidate = base;
        int suffix = 1;
        while (slugExists(candidate, selfId)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private boolean slugExists(String slug, Long selfId) {
        return userAccountRepository.findBySlug(slug)
            .filter(existing -> selfId == null || !existing.getId().equals(selfId))
            .isPresent();
    }

    private String slugify(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_-]+", "-")
            .replaceAll("^-+", "")
            .replaceAll("-+$", "")
            .replaceAll("-{2,}", "-");
        return normalized;
    }

    private void validateProfileSlug(String slug, Long selfId) {
        String normalized = slugify(slug);
        if (normalized == null || normalized.isBlank()) {
            throw new RuntimeException("Slug 不能为空");
        }
        if (normalized.length() < 3 || normalized.length() > 50) {
            throw new RuntimeException("Slug 长度必须在 3-50 个字符之间");
        }
        if (!normalized.equals(slug)) {
            throw new RuntimeException("Slug 仅支持小写字母、数字、下划线和连字符");
        }
        if (slugExists(normalized, selfId)) {
            throw new RuntimeException("Slug 已被其他用户使用");
        }
    }

    private String resolveAvatarExtension(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        String extension = null;
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        }
        if (extension == null || extension.isBlank()) {
            String contentType = file.getContentType();
            if ("image/png".equalsIgnoreCase(contentType)) {
                extension = ".png";
            } else if ("image/webp".equalsIgnoreCase(contentType)) {
                extension = ".webp";
            } else {
                extension = ".jpg";
            }
        }
        if (!java.util.Set.of(".jpg", ".jpeg", ".png", ".webp").contains(extension)) {
            throw new RuntimeException("头像仅支持 jpg、png、webp 格式");
        }
        return ".jpeg".equals(extension) ? ".jpg" : extension;
    }
}
