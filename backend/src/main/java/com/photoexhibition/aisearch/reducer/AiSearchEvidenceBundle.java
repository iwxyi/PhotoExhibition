package com.photoexhibition.aisearch.reducer;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AiSearchEvidenceBundle {
    private String query;
    private String queryMode;
    private String planType;
    private String evidenceStatus;
    private Map<String, Object> summary = new LinkedHashMap<>();
}
