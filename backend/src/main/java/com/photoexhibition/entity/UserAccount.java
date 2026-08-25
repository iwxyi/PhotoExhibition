package com.photoexhibition.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_account")
@Data
public class UserAccount {
    public static final long DEFAULT_STORAGE_QUOTA_BYTES = 3L * 1024 * 1024 * 1024;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(unique = true, length = 32)
    private String phone;

    @Column(unique = true, length = 150)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(name = "admin_color_mode", length = 16)
    private String adminColorMode = "dark";

    @Column(name = "nickname", length = 100)
    private String nickname;

    @Column(name = "project_name_zh", length = 200)
    private String projectNameZh;

    @Column(name = "project_name_en", length = 200)
    private String projectNameEn;

    @Column(name = "avatar_path", length = 500)
    private String avatarPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.USER_ADMIN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.PENDING;

    @Column(name = "phone_verified", nullable = false)
    private Boolean phoneVerified = false;

    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    @Column(name = "storage_quota_bytes", nullable = false)
    private Long storageQuotaBytes = DEFAULT_STORAGE_QUOTA_BYTES;

    @Column(name = "vip_extra_quota_bytes", nullable = false)
    private Long vipExtraQuotaBytes = 0L;

    @Column(name = "current_vip_plan_id")
    private Long currentVipPlanId;

    @Column(name = "vip_expire_at")
    private LocalDateTime vipExpireAt;

    @Column(name = "storage_used_bytes", nullable = false)
    private Long storageUsedBytes = 0L;

    @Column(name = "preferred_storage_provider_id")
    private Long preferredStorageProviderId;

    @Column(name = "multi_user_visible", nullable = false)
    private Boolean multiUserVisible = true;

    @Column(name = "login_attempts", nullable = false)
    private Integer loginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "last_login_ip", length = 64)
    private String lastLoginIp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
