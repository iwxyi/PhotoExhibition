package com.photoexhibition.aisearch.validation;

import lombok.Data;

@Data
public class AiSearchAnalysisFallbackRequest {
    private String routingType;
    private String resolvedQuery;
    private boolean explicitAnchorPerson;
    private Integer leftYear;
    private Integer rightYear;
    private String keywordSummary;
}
