package com.photoexhibition.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Durable queue entry for visual analysis.  Kept separate from scan_task because
 * its checkpoint is a selected, finite photo set rather than a file-system walk.
 */
@Entity
@Table(name = "photo_visual_analysis_job")
@Data
public class PhotoVisualAnalysisJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "requested_by_user_id") private Long requestedByUserId;
    @Column(nullable = false, length = 30) private String status = "QUEUED";
    @Column(name = "photo_ids_json", nullable = false, columnDefinition = "TEXT") private String photoIdsJson;
    @Column(name = "force_reanalyze", nullable = false) private Boolean forceReanalyze = true;
    @Column(name = "total_items", nullable = false) private Integer totalItems = 0;
    @Column(name = "processed_items", nullable = false) private Integer processedItems = 0;
    @Column(name = "skipped_items", nullable = false) private Integer skippedItems = 0;
    @Column(name = "failed_items", nullable = false) private Integer failedItems = 0;
    @Column(name = "last_photo_id") private Long lastPhotoId;
    @Column(name = "error_message", length = 1000) private String errorMessage;
    @Column(name = "started_at") private LocalDateTime startedAt;
    @Column(name = "finished_at") private LocalDateTime finishedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
