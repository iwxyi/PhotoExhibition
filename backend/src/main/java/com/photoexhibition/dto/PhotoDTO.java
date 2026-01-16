package com.photoexhibition.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class PhotoDTO {
    private Long id;
    private Long albumId;
    private String filename;
    private String originalPath;
    private String thumbnailPath;
    private String webpPath;
    private String smallThumbPath;
    private String mediumThumbPath;
    private String largeThumbPath;
    private Long fileSize;
    private String contentHash;
    private Integer width;
    private Integer height;
    private String format;
    private String dominantColor;
    private String colorCategory;
    private List<String> colorPalette;
    private Map<String, Object> exifData;
    private String cameraMake;
    private String cameraModel;
    private String lensModel;
    private String focalLength;
    private String aperture;
    private String shutterSpeed;
    private Integer iso;
    private LocalDateTime takenAt;
    private Double qualityScore;
    private Double focusX; // 焦点X位置（百分比 0-100）
    private Double focusY; // 焦点Y位置（百分比 0-100）
    private Integer viewCount;
    private Integer likeCount;
    private Boolean isFeatured;
    private List<TagDTO> tags;
    // 如果图片被指派给某个人物（非人脸关联），在此记录
    private Long assignedPersonId;
    private String assignedPersonName;
    private List<FaceDTO> faces;
    private LocalDateTime createdAt;
}

