package com.photoexhibition.dto;

import lombok.Data;

@Data
public class PersonListItemDTO {
    private String type; // "confirmed" 或 "cluster"
    private Long id; // 人物ID（confirmed）或聚类索引（cluster）
    private String name; // 人物名称或聚类标识
    private String description; // 人物描述
    private Integer faceCount; // 人脸数量
    private Long sampleFaceId; // 代表脸ID
    private Long samplePhotoId; // 代表照片ID
    private String sampleThumbnailPath; // 代表脸缩略图
    private String sampleOriginalPath; // 代表脸原图
    private Double sampleConfidence; // 代表脸置信度
    private Double avgConfidence; // 平均置信度（聚类用）
    private Boolean hidden;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
}

