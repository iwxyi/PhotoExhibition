package com.photoexhibition.dto;

import lombok.Data;
import java.util.Arrays;
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
    private String colorCategory;        // 颜色分类
    private String category;             // 相册分类
    private Double minQualityScore;      // 最小质量评分
    private Boolean randomOrder = false;     // 是否随机排序
    private List<Long> excludePhotoIds = java.util.Arrays.asList(-1L);      // 排除的图片ID列表（避免重复显示，默认-1表示不排除）
    private Integer page = 0;
    private Integer size = 20;

    public Integer getPage() {
        return page == null || page < 0 ? 0 : page;
    }

    public Integer getSize() {
        return size == null || size < 1 ? 20 : size;
    }
}

