package com.photoexhibition.aisearch.plan;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class AiSearchPlan {
    private String version = "v2";
    private String query;
    private String queryMode;
    private String planType;
    private List<String> resultTypes = new ArrayList<>();
    private Integer maxEvidenceItems = 40;
    private List<AiSearchPlanStep> steps = new ArrayList<>();
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
