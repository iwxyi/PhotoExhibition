package com.photoexhibition.aisearch.validation;

import lombok.Data;

import java.util.List;

@Data
public class AiSearchAnalysisFallbackRequest {
    private String routingType;
    private String resolvedQuery;
    private boolean explicitAnchorPerson;
    private Integer leftYear;
    private Integer rightYear;
    private String keywordSummary;
    private List<String> topicKeywords;
    private boolean aiDerived;
}
