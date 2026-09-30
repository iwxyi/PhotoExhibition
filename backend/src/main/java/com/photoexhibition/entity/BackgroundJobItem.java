package com.photoexhibition.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "background_job_item",
    uniqueConstraints = @UniqueConstraint(name = "uk_background_job_item", columnNames = {"job_id", "target_key", "stage"}),
    indexes = {
        @Index(name = "idx_background_job_item_claim", columnList = "job_id,status,id"),
        @Index(name = "idx_background_job_item_dedup", columnList = "owner_user_id,target_key,stage,pipeline_version,parameters_hash,status")
    })
@Data
public class BackgroundJobItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "photo_id")
    private Long photoId;

    @Column(name = "target_key", nullable = false, length = 200)
    private String targetKey;

    @Column(nullable = false, length = 60)
    private String stage;

    @Column(name = "pipeline_version", nullable = false, length = 40)
    private String pipelineVersion = "1";

    @Column(name = "parameters_hash", nullable = false, length = 64)
    private String parametersHash = "default";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BackgroundJobItemStatus status = BackgroundJobItemStatus.QUEUED;

    @Column(name = "requires_scan_complete", nullable = false)
    private Boolean requiresScanComplete = true;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(nullable = false)
    private Boolean retryable = true;

    @Column(name = "failure_group_id", length = 80)
    private String failureGroupId;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /** Earliest time a transient failure may be claimed again. */
    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
