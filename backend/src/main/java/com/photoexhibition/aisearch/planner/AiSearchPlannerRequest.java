package com.photoexhibition.aisearch.planner;

import com.photoexhibition.dto.AiSearchIntent;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AiSearchPlannerRequest {
    private String query;
    private String queryMode;
    private AiSearchIntent legacyIntent;
    private Map<String, Object> candidateSummary = new LinkedHashMap<>();
    private Map<String, Object> hints = new LinkedHashMap<>();
}
