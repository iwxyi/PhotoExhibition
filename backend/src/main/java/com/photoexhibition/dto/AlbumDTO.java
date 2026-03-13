package com.photoexhibition.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AlbumDTO {
    private Long id;
    private String name;
    private String path;
    private Long coverImageId;
    /**
     * 自定义封面图片ID列表（最多4张）
     */
    private java.util.List<Long> coverImageIds;
    private String description;
    private Integer photoCount;
    private Boolean aggregateSubAlbums;
    private Boolean downloadAllowed;
    private Boolean isHidden;
    private String photoSortOrder;
    private Boolean hasSubAlbums;
    private Boolean isTopLevel;
    /**
     * 展示用标题（去掉日期前缀、合并子目录），便于前端显示
     */
    private String displayTitle;
    /**
     * 一级分类（base-path 下的第一层目录）
     */
    private String category;
    private List<TagDTO> tags;
    private CoverImagesDTO coverImages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /**
     * 相册的拍摄日期（取最早的照片拍摄时间），用于前端显示
     */
    private LocalDateTime takenAt;

    /**
     * 相对路径（去掉 base-path），便于前端显示
     */
    private String relativePath;

    // 氛围信息
    private String backgroundColor;
    private String foregroundColor;
    private String navbarColor;
    private List<AtmosphereEffectDTO> atmosphereEffects;
}

