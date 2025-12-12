package com.photoexhibition.dto;

import lombok.Data;

@Data
public class FaceDTO {
    private Long id;
    private Long photoId;
    private Double x;
    private Double y;
    private Double width;
    private Double height;
    private Double confidence;
    private Long personId;
    private String personName;
    private String personDescription;
    private String photoFilename;
    private String photoThumbnailPath;
    private String photoOriginalPath;
    private Double similarity; // 相似度（可选）
    private Boolean isConfirmed; // 是否已确认
}

