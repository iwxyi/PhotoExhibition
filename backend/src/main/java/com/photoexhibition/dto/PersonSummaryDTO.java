package com.photoexhibition.dto;

import lombok.Data;

@Data
public class PersonSummaryDTO {
    private Long id;
    private String name;
    private String description;
    private Long samplePhotoId;
    private Long sampleFaceId;
    private String sampleThumbnailPath;
    private String sampleOriginalPath;
    private Double sampleConfidence;
    private Integer faceCount; // 人脸数量
    private Integer albumCount; // 相册数量
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
}

