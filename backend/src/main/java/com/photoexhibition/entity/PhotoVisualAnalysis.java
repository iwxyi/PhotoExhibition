package com.photoexhibition.entity;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "photo_visual_analysis", uniqueConstraints = @UniqueConstraint(columnNames = "photo_id"))
@Data
public class PhotoVisualAnalysis {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "photo_id", nullable = false) private Long photoId;
    @Column(name = "user_id") private Long userId;
    @Column(nullable = false, length = 30) private String status = "PENDING";
    @Column(columnDefinition = "TEXT") private String caption;
    @Column(name = "analysis_json", columnDefinition = "TEXT") private String analysisJson;
    @Column(name = "model_name", length = 200) private String modelName;
    @Column(name = "prompt_version", length = 50) private String promptVersion = "visual-v1";
    @Column(name = "error_message", length = 1000) private String errorMessage;
    @Column(name = "analyzed_at") private LocalDateTime analyzedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
