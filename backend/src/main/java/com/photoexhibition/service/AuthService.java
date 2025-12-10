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

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AdminUserRepository adminUserRepository;
    private final JwtConfig jwtConfig;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public LoginResponse login(LoginRequest request) {
        log.info("登录: {}", request.getUsername());
        
        AdminUser user = adminUserRepository.findByUsername(request.getUsername())
                .orElse(null);
        
        if (user == null) {
            log.warn("登录失败: 用户不存在 - {}", request.getUsername());
            throw new RuntimeException("用户名或密码错误");
        }

        if (!user.getEnabled()) {
            log.warn("登录失败: 账户已禁用 - {}", request.getUsername());
            throw new RuntimeException("账户已被禁用");
        }

        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), user.getPassword());
        
        if (!passwordMatches) {
            log.warn("登录失败: 密码错误 - {}", request.getUsername());
            throw new RuntimeException("用户名或密码错误");
        }

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
}

