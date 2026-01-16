package com.photoexhibition.dto;

import lombok.Data;
import java.util.List;

@Data
public class FilterRequest {
    private List<Long> tagIds;           // 标签ID列表
    private Long personId;               // 人物ID（按人物筛选照片）
    private String cameraModel;          // 相机型号
    private String lensModel;            // 镜头型号
    private Double minAperture;          // 最小光圈
    private Double maxAperture;          // 最大光圈
    private Double minFocalLength;       // 最小焦距
    private Double maxFocalLength;       // 最大焦距
    private Double minShutterSpeed;      // 最小快门速度
    private Double maxShutterSpeed;      // 最大快门速度
    private Integer minIso;              // 最小ISO
    private Integer maxIso;              // 最大ISO
    private String dominantColor;        // 主色调
    private Double minQualityScore;      // 最小质量评分
    private Integer page = 0;
    private Integer size = 20;
}

