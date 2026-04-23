package com.photoexhibition.aisearch.executor;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AiSearchExecutionContext {
    private String query;
    private String queryMode;
    private Map<String, Object> values = new LinkedHashMap<>();
}
