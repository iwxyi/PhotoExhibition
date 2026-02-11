package com.photoexhibition.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AlbumRecommendationDTO {
    private Long albumId;
    private String albumName;
    private String albumPath;
    private Integer photoCount;
    private Integer similarFaceCount;
    private Integer claimedPhotoCount; // 已认领的图片数量（含人脸认领与图片指派）
    private List<FaceDTO> similarFaces;
    private LocalDateTime takenAt;
    private String coverImagePath; // 兼容旧版本：单张封面图路径
    // 最多3张封面图，用于人物详情页的相册展示
    private String coverImagePath1; // 第一张封面图
    private String coverImagePath2; // 第二张封面图
    private String coverImagePath3; // 第三张封面图
}
