package com.photoexhibition.dto;

import lombok.Data;

@Data
public class AiSearchAnalysisSpec {
    private String subjectType;
    private AiSearchAnalysisSubject subject;
    private AiSearchAnalysisOperation operation;
    private AiSearchAnalysisScope scope;
}
