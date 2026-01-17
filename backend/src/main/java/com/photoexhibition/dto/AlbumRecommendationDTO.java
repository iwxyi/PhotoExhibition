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
    private List<FaceDTO> similarFaces;
    private LocalDateTime takenAt;
    private String coverImagePath; // 相册封面图片路径
}
