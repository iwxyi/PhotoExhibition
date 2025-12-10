package com.photoexhibition.dto;

import lombok.Data;
import java.util.List;

@Data
public class FilterRequest {
    private List<Long> tagIds;           // 标签ID列表
    private String cameraModel;          // 相机型号
    private String lensModel;            // 镜头型号
    private Double minAperture;          // 最小光圈
    private Double maxAperture;          // 最大光圈
    private Integer minIso;              // 最小ISO
    private Integer maxIso;              // 最大ISO
    private String dominantColor;        // 主色调
    private Double minQualityScore;      // 最小质量评分
    private Integer page = 0;
    private Integer size = 20;
}

