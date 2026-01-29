package com.photoexhibition.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

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
    
    // 多封面图片列表（最多4张，来自不同相册）
    private List<SamplePhotoDTO> samplePhotos = new ArrayList<>();
}
