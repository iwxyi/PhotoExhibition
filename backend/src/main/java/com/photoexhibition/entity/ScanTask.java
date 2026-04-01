package com.photoexhibition.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "scan_task")
@Data
public class ScanTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "requested_by_user_id")
    private Long requestedByUserId;

    @Column(name = "storage_provider_id")
    private Long storageProviderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 30)
    private ScanTaskType taskType = ScanTaskType.INCREMENTAL_SCAN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ScanTaskStatus status = ScanTaskStatus.PENDING;

    @Column(name = "root_path", nullable = false, length = 1000)
    private String rootPath;

    @Column(nullable = false)
    private Integer priority = 100;

    @Column(name = "total_items")
    private Integer totalItems = 0;

    @Column(name = "processed_items")
    private Integer processedItems = 0;

    @Column(name = "skipped_items")
    private Integer skippedItems = 0;

    @Column(name = "failed_items")
    private Integer failedItems = 0;

    @Column(name = "last_processed_path", length = 1000)
    private String lastProcessedPath;

    @Column(name = "checkpoint_json", columnDefinition = "TEXT")
    private String checkpointJson;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "scheduled_task", nullable = false)
    private Boolean scheduledTask = false;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

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
