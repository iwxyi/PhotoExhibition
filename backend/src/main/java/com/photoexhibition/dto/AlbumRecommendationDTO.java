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
    private String coverImagePath; // 相册封面图片路径
}
