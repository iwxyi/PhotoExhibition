package com.photoexhibition.aisearch.executor;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AiSearchExecutionResult {
    private Map<String, Object> stepOutputs = new LinkedHashMap<>();
    private Map<String, Object> finalOutputs = new LinkedHashMap<>();
}
