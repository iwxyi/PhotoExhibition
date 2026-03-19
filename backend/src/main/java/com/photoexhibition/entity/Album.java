package com.photoexhibition.entity;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "album")
@Data
public class Album {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 500)
    private String path;

    @Column(name = "path_hash", length = 64, unique = true)
    private String pathHash;

    @Column(name = "cover_image_id")
    private Long coverImageId;

    /**
     * 自定义封面图片ID列表（最多4张）
     * 有自定义封面时优先使用，没有时使用自动生成的封面
     */
    @Column(name = "cover_image_ids", columnDefinition = "TEXT")
    private String coverImageIds;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "photo_count")
    private Integer photoCount = 0;

    @Column(name = "aggregate_sub_albums")
    private Boolean aggregateSubAlbums = false;

    @Column(name = "download_allowed")
    private Boolean downloadAllowed;

    @Column(name = "is_hidden")
    private Boolean isHidden = false;

    @Column(name = "photo_sort_order")
    private String photoSortOrder;

    // 氛围颜色字段（深色/浅色模式各一套背景色+点缀色）
    @Column(name = "dark_bg_color", length = 20)
    private String darkBgColor;

    @Column(name = "light_bg_color", length = 20)
    private String lightBgColor;

    @Column(name = "dark_accent_color", length = 20)
    private String darkAccentColor;

    @Column(name = "light_accent_color", length = 20)
    private String lightAccentColor;

    @Column(name = "atmosphere_effects", columnDefinition = "JSON")
    private String atmosphereEffects;

    @Column(name = "atmosphere_last_updated")
    private LocalDateTime atmosphereLastUpdated;

    @Column(name = "latest_photo_taken_at")
    private LocalDateTime latestPhotoTakenAt;

    @Column(name = "album_name_date")
    private LocalDateTime albumNameDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "album_tag",
        joinColumns = @JoinColumn(name = "album_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private List<Tag> tags;

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

