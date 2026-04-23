package com.photoexhibition.aisearch.operator;

import com.photoexhibition.aisearch.executor.AiSearchExecutionContext;
import com.photoexhibition.aisearch.model.AiSearchPersonAggregate;
import com.photoexhibition.aisearch.model.AiSearchPersonGrowthAggregate;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DeriveCandidateBodyChangeOperator implements AiSearchOperator {

    private final PersonGrowthSignalAnalyzer personGrowthSignalAnalyzer;

    @Override
    public String getName() {
        return "derive_candidate_body_change";
    }

    @Override
    public Object execute(AiSearchPlanStep step, AiSearchExecutionContext context) {
        Object input = context.getValues().get(step.getInputRef());
        if (!(input instanceof List)) {
            return Collections.emptyList();
        }

        Integer startYear = toInteger(step.getArgs().get("startYear"));
        Integer endYear = toInteger(step.getArgs().get("endYear"));
        String desiredTrend = asString(step.getArgs().get("desiredTrend"));
        if (startYear == null || endYear == null) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<AiSearchPersonAggregate> candidates = (List<AiSearchPersonAggregate>) input;
        List<AiSearchPersonGrowthAggregate> results = new ArrayList<>();
        for (AiSearchPersonAggregate candidate : candidates) {
            if (candidate == null || candidate.getPersonId() == null) {
                continue;
            }
            Map<String, Object> metrics = personGrowthSignalAnalyzer.analyze(
                candidate.getPersonId(),
                candidate.getPersonName(),
                startYear,
                endYear
            );
            String trend = asString(metrics.get("trend"));
            if (desiredTrend != null && !desiredTrend.isBlank() && !desiredTrend.equals(trend)) {
                continue;
            }
            AiSearchPersonGrowthAggregate aggregate = toAggregate(metrics);
            if (aggregate != null && aggregate.getPersonName() != null && !aggregate.getPersonName().isBlank()) {
                results.add(aggregate);
            }
        }
        return results;
    }

    private AiSearchPersonGrowthAggregate toAggregate(Map<String, Object> metrics) {
        String trend = asString(metrics.get("trend"));
        int totalPhotos = toInteger(metrics.get("totalPhotos")) == null ? 0 : toInteger(metrics.get("totalPhotos"));
        if (trend == null || totalPhotos <= 0 || "unknown".equals(trend) || "insufficient_data".equals(trend) || "error".equals(trend)) {
            return null;
        }
        AiSearchPersonGrowthAggregate aggregate = new AiSearchPersonGrowthAggregate();
        aggregate.setPersonId(toLong(metrics.get("personId")));
        aggregate.setPersonName(asString(metrics.get("personName")));
        aggregate.setMatchedPhotoCount(totalPhotos);
        aggregate.setMatchedLastSeen(toDateTime(metrics.get("matchedLastSeen")));
        aggregate.setTrend(trend);
        aggregate.setChangePercent(toDouble(metrics.get("changePercent")));
        aggregate.setFirstPeriod(asString(metrics.get("firstPeriod")));
        aggregate.setLastPeriod(asString(metrics.get("lastPeriod")));
        aggregate.setFirstRatio(toDouble(metrics.get("firstRatio")));
        aggregate.setLastRatio(toDouble(metrics.get("lastRatio")));
        return aggregate;
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

    private Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String && !((String) value).isBlank()) {
            return Long.parseLong((String) value);
        }
        return null;
    }

    private Double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private LocalDateTime toDateTime(Object value) {
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime();
        }
        return null;
    }
}
