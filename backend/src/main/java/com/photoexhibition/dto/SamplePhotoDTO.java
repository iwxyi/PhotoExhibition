package com.photoexhibition.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 样本人脸照片信息（用于多封面展示）
 */
@Data
public class SamplePhotoDTO {
    private Long photoId;
    private Long faceId;
    private String thumbnailPath;
    private String originalPath;
    private Long albumId;       // 相册ID（用于去重）
    private LocalDateTime albumDate; // 相册时间（用于排序）
    private Integer likeCount;  // 点赞数量
    private Double score;       // AI评分
    private LocalDateTime createdAt; // 照片创建时间
}

