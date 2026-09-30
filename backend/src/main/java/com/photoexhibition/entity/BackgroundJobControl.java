package com.photoexhibition.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "background_job_control", uniqueConstraints =
    @UniqueConstraint(name = "uk_background_job_control_scope", columnNames = {"scope_type", "scope_key"}))
@Data
public class BackgroundJobControl {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scope_type", nullable = false, length = 20)
    private String scopeType;

    @Column(name = "scope_key", nullable = false, length = 80)
    private String scopeKey;

    @Column(nullable = false)
    private Boolean paused = false;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
