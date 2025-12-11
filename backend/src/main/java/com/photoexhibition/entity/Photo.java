package com.photoexhibition.entity;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "photo")
@Data
public class Photo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "album_id", nullable = false)
    private Long albumId;

    @Column(nullable = false)
    private String filename;

    @Column(name = "original_path", nullable = false, length = 1000)
    private String originalPath;

    @Column(name = "thumbnail_path", length = 1000)
    private String thumbnailPath;

    @Column(name = "webp_path", length = 1000)
    private String webpPath;

    @Column(name = "file_size")
    private Long fileSize;

    private Integer width;
    private Integer height;
    private String format;

    @Column(name = "dominant_color", length = 20)
    private String dominantColor;

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

    private Integer iso;

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

    @Column(name = "is_featured")
    private Boolean isFeatured = false;

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

