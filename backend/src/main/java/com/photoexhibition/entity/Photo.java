package com.photoexhibition.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "photo")
@Data
@EqualsAndHashCode(exclude = {"tags", "faces", "aiScoring"})
public class Photo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "album_id", nullable = false)
    private Long albumId;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String filename;

    @Column(name = "original_path", nullable = false, length = 1000)
    private String originalPath;

    @Column(name = "thumbnail_path", length = 1000)
    private String thumbnailPath;

    @Column(name = "webp_path", length = 1000)
    private String webpPath;

    @Column(name = "small_thumb_path", length = 1000)
    private String smallThumbPath;

    @Column(name = "medium_thumb_path", length = 1000)
    private String mediumThumbPath;

    @Column(name = "large_thumb_path", length = 1000)
    private String largeThumbPath;

    /**
     * 背景移除后的图片路径（透明PNG）
     */
    @Column(name = "background_removed_path", length = 1000)
    private String backgroundRemovedPath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "content_hash", length = 64, unique = true)
    private String contentHash;

    @Column(name = "path_hash", length = 64, unique = true)
    private String pathHash;

    @Column(name = "canonical_photo_id")
    private Long canonicalPhotoId;

    private Integer width;
    private Integer height;
    private String format;

    @Column(name = "dominant_color", length = 20)
    private String dominantColor;

    @Column(name = "color_category", length = 20)
    private String colorCategory;

    @Column(name = "color_palette", columnDefinition = "JSON")
    private String colorPalette;

    @Column(name = "exif_data", columnDefinition = "JSON")
    private String exifData;

    @Column(name = "camera_make", length = 100)
    private String cameraMake;

    @Column(name = "camera_model", length = 100)
    private String cameraModel;

    @Column(name = "lens_model", length = 100)
    private String lensModel;

    @Column(name = "focal_length", length = 50)
    private String focalLength;

    @Column(length = 50)
    private String aperture;

    @Column(name = "shutter_speed", length = 50)
    private String shutterSpeed;

    @Column(name = "iso")
    private Integer iso;
    
    @Column(name = "shutter_speed_seconds")
    private Double shutterSpeedSeconds;

    @Column(name = "focal_length_mm")
    private Double focalLengthMm;

    @Column(name = "aperture_value")
    private Double apertureValue;

    @Column(name = "taken_at")
    private LocalDateTime takenAt;

    @Column(name = "quality_score", precision = 5, scale = 2)
    private Double qualityScore;

    @Column(name = "focus_x", precision = 5, scale = 2)
    private Double focusX; // 焦点X位置（百分比 0-100）

    @Column(name = "focus_y", precision = 5, scale = 2)
    private Double focusY; // 焦点Y位置（百分比 0-100）

    @Column(name = "view_count")
    private Integer viewCount = 0;

    @Column(name = "like_count")
    private Integer likeCount = 0;

    @Column(name = "is_featured")
    private Boolean isFeatured = false;

    /**
     * 是否隐藏：隐藏的照片不会在相册详情页、首页图墙等公开页面显示
     * 但仍会显示在人物图片列表中
     */
    @Column(name = "is_hidden")
    private Boolean isHidden = false;

    // 处理状态跟踪字段
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", length = 50)
    private ProcessingStatus processingStatus = ProcessingStatus.PENDING;

    @Column(name = "processing_errors", columnDefinition = "TEXT")
    private String processingErrors; // JSON格式存储处理过程中的错误信息

    /**
     * 获取处理状态
     */
    public ProcessingStatus getProcessingStatus() {
        return processingStatus != null ? processingStatus : ProcessingStatus.PENDING;
    }

    /**
     * 设置处理状态
     */
    public void setProcessingStatus(ProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    /**
     * 更新处理状态到下一个步骤
     */
    public void advanceProcessingStatus() {
        this.processingStatus = getProcessingStatus().getNextStep();
    }

    /**
     * 标记处理失败
     */
    public void markProcessingFailed(String error) {
        this.processingStatus = ProcessingStatus.FAILED;
        addProcessingError(error);
    }

    /**
     * 添加处理错误信息
     */
    public void addProcessingError(String error) {
        if (this.processingErrors == null) {
            this.processingErrors = error;
        } else {
            this.processingErrors += "; " + error;
        }
    }

    /**
     * 检查是否可以跳过处理（用于断点续上）
     */
    public boolean canSkipProcessing(boolean force) {
        return getProcessingStatus().canSkip() && !force;
    }

    /**
     * 检查是否需要重新处理
     */
    public boolean needsReprocessing(boolean force) {
        return getProcessingStatus().needsReprocessing(force);
    }

    /**
     * 检查是否需要继续处理
     */
    public boolean needsContinuation(boolean force) {
        return getProcessingStatus().needsContinuation(force);
    }

    @ManyToMany(fetch = FetchType.EAGER, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "photo_tag",
        joinColumns = @JoinColumn(name = "photo_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    @OneToMany(mappedBy = "photo", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Face> faces = new ArrayList<>();

    @OneToOne(mappedBy = "photo", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private PhotoAIScoring aiScoring;

    // AI增强分析字段
    @Column(name = "scene_analysis", columnDefinition = "JSON")
    private String sceneAnalysis; // 场景识别结果，JSON格式

    @Column(name = "emotion_analysis", columnDefinition = "JSON")
    private String emotionAnalysis; // 情感分析结果，JSON格式

    @Column(name = "primary_scene", length = 100)
    private String primaryScene; // 主要场景分类

    @Column(name = "primary_emotion", length = 50)
    private String primaryEmotion; // 主要情感

    @Column(name = "scene_confidence")
    private Float sceneConfidence; // 场景识别置信度

    @Column(name = "emotion_confidence")
    private Float emotionConfidence; // 情感分析置信度

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
