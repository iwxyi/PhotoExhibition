package com.photoexhibition.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiSearchAnalysisHint {
    private String capability;
    private Integer targetYear;
    private Integer activeYear;
    private Integer absentYear;
    private Integer presentYear;
    private Integer missingAgainYear;
    private Integer startYear;
    private Integer endYear;
    private String desiredTrend;
    private List<String> cameraModels;
    private List<String> lensModels;
    private List<String> scopeKeywords;
    private List<Long> anchorPersonIds;
    private List<String> anchorPersonNames;
}
