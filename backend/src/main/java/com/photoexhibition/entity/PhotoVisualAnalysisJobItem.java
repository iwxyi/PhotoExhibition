package com.photoexhibition.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "photo_visual_analysis_job_item",
    uniqueConstraints = @UniqueConstraint(columnNames = {"job_id", "photo_id"}))
@Data
public class PhotoVisualAnalysisJobItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "job_id", nullable = false) private Long jobId;
    @Column(name = "photo_id", nullable = false) private Long photoId;
    @Column(name = "photo_name", length = 500) private String photoName;
    @Column(nullable = false, length = 30) private String status = "QUEUED";
    @Column(name = "error_message", length = 1000) private String errorMessage;
    @Column(name = "started_at") private LocalDateTime startedAt;
    @Column(name = "finished_at") private LocalDateTime finishedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
