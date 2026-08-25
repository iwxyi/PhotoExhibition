package com.photoexhibition.dto;

import lombok.Data;

@Data
public class AiSearchAnalysisSubject {
    private String type;
    private Integer targetYear;
    private Integer absentYear;
    private Integer presentYear;
    private Integer missingAgainYear;
}
