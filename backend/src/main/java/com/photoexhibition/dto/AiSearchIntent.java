package com.photoexhibition.dto;

import lombok.Data;
import java.util.List;

@Data
public class AiSearchIntent {
    private Long personId;
    private List<Long> tagIds;
    private List<Long> albumIds;
    private String startDate;
    private String endDate;
    private String cameraModel;
    private String lensModel;
    private Double minFocalLength;
    private Double maxFocalLength;
    private Double minAperture;
    private Double maxAperture;
    private Double minShutterSpeed;
    private Double maxShutterSpeed;
    private Integer minIso;
    private Integer maxIso;
    private String colorCategory;
    private Double minQualityScore;
    private List<String> keywords;
    private String explanation;

    // 新增字段：混合结果类型支持
    private List<Long> personIds;           // 多人物搜索（替代单个 personId）
    private List<String> resultTypes;       // GPT 决定返回哪些类型: "albums", "persons", "photos"
    private boolean includeHidden;          // 是否包含隐藏内容
    private List<String> filenameKeywords;  // 文件名搜索词
}
