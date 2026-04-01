package com.photoexhibition.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "login_record")
@Data
public class LoginRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username_snapshot", length = 50)
    private String usernameSnapshot;

    @Column(name = "phone_snapshot", length = 32)
    private String phoneSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "login_method", nullable = false, length = 30)
    private LoginMethod loginMethod = LoginMethod.USERNAME_PASSWORD;

    @Column(nullable = false)
    private Boolean success = false;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
