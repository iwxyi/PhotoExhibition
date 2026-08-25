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

    private List<Long> personIds;
    private List<String> resultTypes;
    private boolean includeHidden;
    private List<String> filenameKeywords;

    private List<AiSearchCondition> must;
    private List<AiSearchCondition> should;
    private List<AiSearchCondition> mustNot;

    private Boolean needAnswer;
    private String answerPrompt;
    private String answerStyle;
    private AiSearchAnalysisSpec analysisSpec;
    private AiSearchAnalysisHint analysisHint;
}
