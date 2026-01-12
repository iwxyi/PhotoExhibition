package com.photoexhibition.service;

import com.photoexhibition.config.JwtConfig;
import com.photoexhibition.dto.LoginRequest;
import com.photoexhibition.dto.LoginResponse;
import com.photoexhibition.entity.AdminUser;
import com.photoexhibition.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AdminUserRepository adminUserRepository;
    private final JwtConfig jwtConfig;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // 登录失败次数限制配置
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        log.info("登录: {}", request.getUsername());

        AdminUser user = adminUserRepository.findByUsername(request.getUsername())
                .orElse(null);

        if (user == null) {
            // 检查是否需要自动创建管理员账户
            if (adminUserRepository.count() == 0) {
                return createDefaultAdmin(request);
            }
            log.warn("登录失败: 用户不存在 - {}", request.getUsername());
            throw new RuntimeException("用户名或密码错误");
        }

        if (!user.getEnabled()) {
            log.warn("登录失败: 账户已禁用 - {}", request.getUsername());
            throw new RuntimeException("账户已被禁用");
        }

        // 检查账户是否被锁定
        if (isAccountLocked(user)) {
            log.warn("登录失败: 账户已被锁定 - {}", request.getUsername());
            throw new RuntimeException("账户已被锁定，请稍后再试");
        }

        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), user.getPassword());

        if (!passwordMatches) {
            log.warn("登录失败: 密码错误 - {}", request.getUsername());
            handleFailedLogin(user);
            throw new RuntimeException("用户名或密码错误");
        }

        // 登录成功，重置失败次数
        resetLoginAttempts(user);
        String token = jwtConfig.generateToken(user.getUsername(), user.getRole());
        log.info("登录成功: {}", user.getUsername());
        return new LoginResponse(token, user.getUsername(), user.getRole(), "登录成功");
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

    /**
     * 检查账户是否被锁定
     */
    private boolean isAccountLocked(AdminUser user) {
        return user.getLockedUntil() != null && LocalDateTime.now().isBefore(user.getLockedUntil());
    }

    /**
     * 处理登录失败
     */
    private void handleFailedLogin(AdminUser user) {
        LocalDateTime now = LocalDateTime.now();
        user.setLastLoginAttempt(now);
        user.setLoginAttempts(user.getLoginAttempts() + 1);

        // 如果达到最大尝试次数，锁定账户
        if (user.getLoginAttempts() >= MAX_LOGIN_ATTEMPTS) {
            user.setLockedUntil(now.plusMinutes(LOCK_DURATION_MINUTES));
            log.warn("账户已锁定 {} 分钟: {}", LOCK_DURATION_MINUTES, user.getUsername());
        }

        adminUserRepository.save(user);
    }

    /**
     * 重置登录尝试次数
     */
    private void resetLoginAttempts(AdminUser user) {
        user.setLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAttempt(null);
        adminUserRepository.save(user);
    }

    /**
     * 更改用户名
     */
    @Transactional
    public LoginResponse changeUsername(String currentUsername, String newUsername, String password) {
        log.info("用户名更改请求: {} -> {}", currentUsername, newUsername);

        // 验证新用户名格式
        if (newUsername == null || newUsername.trim().isEmpty()) {
            throw new RuntimeException("新用户名不能为空");
        }

        if (newUsername.length() < 3 || newUsername.length() > 50) {
            throw new RuntimeException("用户名长度必须在3-50个字符之间");
        }

        if (!newUsername.matches("^[a-zA-Z0-9_-]+$")) {
            throw new RuntimeException("用户名只能包含字母、数字、下划线和连字符");
        }

        // 查找当前用户
        AdminUser user = adminUserRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 验证密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("密码验证失败");
        }

        // 检查新用户名是否已被使用
        if (adminUserRepository.existsByUsername(newUsername)) {
            throw new RuntimeException("用户名已被使用");
        }

        // 更新用户名
        String oldUsername = user.getUsername();
        user.setUsername(newUsername);
        adminUserRepository.save(user);

        // 生成新的token（因为JWT包含用户名）
        String newToken = jwtConfig.generateToken(newUsername, user.getRole());

        log.info("用户名更改成功: {} -> {}", oldUsername, newUsername);

        return new LoginResponse(newToken, newUsername, user.getRole(), "用户名修改成功，请使用新用户名重新登录");
    }

    /**
     * 创建默认管理员账户（当数据库中没有管理员账户时）
     */
    @Transactional
    private LoginResponse createDefaultAdmin(LoginRequest request) {
        log.info("数据库中没有管理员账户，开始自动创建默认管理员账户");

        // 使用默认凭证验证
        String defaultUsername = "admin";
        String defaultPassword = "admin";

        if (!defaultUsername.equals(request.getUsername()) || !defaultPassword.equals(request.getPassword())) {
            throw new RuntimeException("系统尚未初始化，请使用默认凭证登录：用户名 admin，密码 admin");
        }

        // 创建默认管理员账户
        AdminUser adminUser = new AdminUser();
        adminUser.setUsername(defaultUsername);
        adminUser.setPassword(passwordEncoder.encode(defaultPassword));
        adminUser.setRole("ADMIN");
        adminUser.setEnabled(true);
        adminUser.setLoginAttempts(0);

        adminUserRepository.save(adminUser);

        String token = jwtConfig.generateToken(adminUser.getUsername(), adminUser.getRole());
        log.info("默认管理员账户创建成功: {}", adminUser.getUsername());

        return new LoginResponse(token, adminUser.getUsername(), adminUser.getRole(),
                "系统初始化完成！默认管理员账户已创建，请及时修改密码以确保安全");
    }
}

