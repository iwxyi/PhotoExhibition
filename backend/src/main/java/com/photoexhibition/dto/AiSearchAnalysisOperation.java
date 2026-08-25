package com.photoexhibition.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiSearchAnalysisOperation {
    private String type;
    private Integer activeYear;
    private Integer startYear;
    private Integer endYear;
    private Integer leftYear;
    private Integer rightYear;
    private Integer missingAgainYear;
    private String desiredTrend;
    private String subject;
    private List<Long> anchorPersonIds;
    private List<String> anchorPersonNames;
}
