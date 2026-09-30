package com.photoexhibition.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "background_job", indexes = {
    @Index(name = "idx_background_job_dispatch", columnList = "resource_lane,status,priority,created_at"),
    @Index(name = "idx_background_job_owner", columnList = "owner_user_id,created_at")
})
@Data
public class BackgroundJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_type", nullable = false, length = 60)
    private String jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_lane", nullable = false, length = 30)
    private BackgroundJobResourceLane resourceLane;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BackgroundJobStatus status = BackgroundJobStatus.QUEUED;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "requested_by_user_id")
    private Long requestedByUserId;

    @Column(nullable = false)
    private Integer priority = 100;

    @Column(name = "pipeline_version", nullable = false, length = 40)
    private String pipelineVersion = "1";

    @Column(name = "force_reprocess", nullable = false)
    private Boolean forceReprocess = false;

    @Column(name = "source_job_id")
    private Long sourceJobId;

    @Column(name = "total_items", nullable = false)
    private Integer totalItems = 0;

    @Column(name = "processed_items", nullable = false)
    private Integer processedItems = 0;

    @Column(name = "succeeded_items", nullable = false)
    private Integer succeededItems = 0;

    @Column(name = "skipped_items", nullable = false)
    private Integer skippedItems = 0;

    @Column(name = "failed_items", nullable = false)
    private Integer failedItems = 0;

    @Column(name = "pause_requested", nullable = false)
    private Boolean pauseRequested = false;

    @Column(name = "cancel_requested", nullable = false)
    private Boolean cancelRequested = false;

    @Column(name = "blocking_reason", length = 80)
    private String blockingReason;

    @Column(name = "failure_group_id", length = 80)
    private String failureGroupId;

    @Column(name = "error_summary", length = 1000)
    private String errorSummary;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "parameters_json", columnDefinition = "TEXT")
    private String parametersJson;

    @Column(name = "parameters_hash", nullable = false, length = 64)
    private String parametersHash = "default";

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

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
