package com.photoexhibition.aisearch.compatibility;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import com.photoexhibition.aisearch.planner.AiSearchPlanner;
import com.photoexhibition.aisearch.planner.AiSearchPlannerRequest;
import com.photoexhibition.dto.AiSearchCondition;
import com.photoexhibition.dto.AiSearchIntent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LegacyIntentAiSearchPlanner implements AiSearchPlanner {

    @Override
    public AiSearchPlan plan(AiSearchPlannerRequest request) {
        AiSearchPlan plan = new AiSearchPlan();
        plan.setQuery(request.getQuery());
        plan.setQueryMode(request.getQueryMode());
        plan.setPlanType("legacy_intent_bridge");

        AiSearchIntent intent = request.getLegacyIntent();
        if (intent != null && intent.getResultTypes() != null) {
            plan.setResultTypes(new ArrayList<>(intent.getResultTypes()));
        }

        AiSearchPlanStep filterStep = new AiSearchPlanStep();
        filterStep.setId("legacy_filter");
        filterStep.setOperator("filter_photos");
        filterStep.setOutputKey("photo_candidates");
        filterStep.setDescription("将旧版 AiSearchIntent 兼容映射为受控过滤步骤");
        filterStep.setArgs(buildFilterArgs(intent));
        plan.getSteps().add(filterStep);

        AiSearchPlanStep summarizeStep = new AiSearchPlanStep();
        summarizeStep.setId("legacy_summarize");
        summarizeStep.setOperator("summarize");
        summarizeStep.setInputRef("photo_candidates");
        summarizeStep.setOutputKey("legacy_summary");
        summarizeStep.setDescription("输出兼容层摘要，供后续迁移调试使用");
        summarizeStep.getDependsOn().add("legacy_filter");
        plan.getSteps().add(summarizeStep);

        plan.getMetadata().put("source", "AiSearchIntent");
        plan.getMetadata().put("hasLegacyIntent", intent != null);
        return plan;
    }

    private Map<String, Object> buildFilterArgs(AiSearchIntent intent) {
        Map<String, Object> args = new LinkedHashMap<>();
        if (intent == null) {
            return args;
        }

        putIfNotEmpty(args, "personId", intent.getPersonId());
        putIfNotEmpty(args, "personIds", intent.getPersonIds());
        putIfNotEmpty(args, "tagIds", intent.getTagIds());
        putIfNotEmpty(args, "albumIds", intent.getAlbumIds());
        putIfNotEmpty(args, "startDate", intent.getStartDate());
        putIfNotEmpty(args, "endDate", intent.getEndDate());
        putIfNotEmpty(args, "cameraModel", intent.getCameraModel());
        putIfNotEmpty(args, "lensModel", intent.getLensModel());
        putIfNotEmpty(args, "colorCategory", intent.getColorCategory());
        putIfNotEmpty(args, "keywords", intent.getKeywords());
        putIfNotEmpty(args, "filenameKeywords", intent.getFilenameKeywords());
        putIfNotEmpty(args, "must", serializeConditions(intent.getMust()));
        putIfNotEmpty(args, "should", serializeConditions(intent.getShould()));
        putIfNotEmpty(args, "mustNot", serializeConditions(intent.getMustNot()));
        args.put("includeHidden", intent.isIncludeHidden());
        args.put("needAnswer", Boolean.TRUE.equals(intent.getNeedAnswer()));
        putIfNotEmpty(args, "answerPrompt", intent.getAnswerPrompt());
        putIfNotEmpty(args, "answerStyle", intent.getAnswerStyle());
        return args;
    }

    private List<Map<String, Object>> serializeConditions(List<AiSearchCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (AiSearchCondition condition : conditions) {
            if (condition == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", condition.getType());
            item.put("ids", condition.getIds());
            item.put("values", condition.getValues());
            item.put("value", condition.getValue());
            item.put("minValue", condition.getMinValue());
            item.put("maxValue", condition.getMaxValue());
            item.put("startDate", condition.getStartDate());
            item.put("endDate", condition.getEndDate());
            serialized.add(item);
        }
        return serialized;
    }

    private void putIfNotEmpty(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String && ((String) value).isBlank()) {
            return;
        }
        if (value instanceof List && ((List<?>) value).isEmpty()) {
            return;
        }
        target.put(key, value);
    }
}
