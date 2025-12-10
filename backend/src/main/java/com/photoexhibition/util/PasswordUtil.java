package com.photoexhibition.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码工具类
 * 用于生成和验证BCrypt密码hash
 */
public class PasswordUtil {
    
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        
        // 生成admin123的BCrypt hash
        String password = "admin123";
        String hash = encoder.encode(password);
        System.out.println("密码: " + password);
        System.out.println("BCrypt Hash: " + hash);
        
        // 验证hash是否正确
        boolean matches = encoder.matches(password, hash);
        System.out.println("验证结果: " + matches);
        
        // 验证SQL中的hash
        String sqlHash = "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iwK8pJ5C";
        boolean sqlMatches = encoder.matches(password, sqlHash);
        System.out.println("SQL Hash验证结果: " + sqlMatches);
    }
}

