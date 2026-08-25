package com.photoexhibition.aisearch.operator;

import com.photoexhibition.aisearch.executor.AiSearchExecutionContext;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DerivePersonGrowthSignalsOperator implements AiSearchOperator {

    private final PersonGrowthSignalAnalyzer personGrowthSignalAnalyzer;

    @Override
    public String getName() {
        return "derive_person_growth_signals";
    }

    @Override
    public Object execute(AiSearchPlanStep step, AiSearchExecutionContext context) {
        Long personId = toLong(step.getArgs().get("personId"));
        Integer startYear = toInteger(step.getArgs().get("startYear"));
        Integer endYear = toInteger(step.getArgs().get("endYear"));
        String personName = asString(step.getArgs().get("personName"));

        if (personId == null || startYear == null || endYear == null) {
            return errorMetrics(personName, startYear, endYear, "缺少人物或时间范围参数");
        }

        return personGrowthSignalAnalyzer.analyze(personId, personName, startYear, endYear);
    }

    private Map<String, Object> errorMetrics(String personName, Integer startYear, Integer endYear, String message) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("personName", personName);
        metrics.put("startYear", startYear);
        metrics.put("endYear", endYear);
        metrics.put("totalPhotos", 0);
        metrics.put("trend", "error");
        metrics.put("errorMessage", message);
        metrics.put("yearlyStats", Collections.emptyList());
        return metrics;
    }

    private Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String && !((String) value).isBlank()) {
            return Long.parseLong((String) value);
        }
        return null;
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && !((String) value).isBlank()) {
            return Integer.parseInt((String) value);
        }
        return null;
    }

    private double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0D;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
