package com.photoexhibition.aisearch.plan;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class AiSearchPlanStep {
    private String id;
    private String operator;
    private String inputRef;
    private String outputKey;
    private boolean allowEmpty;
    private String description;
    private List<String> dependsOn = new ArrayList<>();
    private Map<String, Object> args = new LinkedHashMap<>();
}
