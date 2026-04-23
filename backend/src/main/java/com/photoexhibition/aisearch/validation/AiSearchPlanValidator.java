package com.photoexhibition.aisearch.validation;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class AiSearchPlanValidator {

    public static final Set<String> ALLOWED_OPERATORS = Set.of(
        "filter_photos",
        "aggregate_persons",
        "aggregate_albums",
        "aggregate_days",
        "set_union",
        "set_intersection",
        "set_difference",
        "sort",
        "limit",
        "summarize",
        "compare_periods",
        "derive_person_growth_signals",
        "derive_candidate_body_change",
        "derive_person_cooccurrence",
        "derive_temporal_person_cooccurrence",
        "derive_person_pair_cooccurrence",
        "derive_temporal_person_pair_cooccurrence",
        "derive_temporal_person_activity"
    );

    public void validate(AiSearchPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("AI 搜索计划不能为空");
        }
        List<AiSearchPlanStep> steps = plan.getSteps();
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("AI 搜索计划至少需要一个步骤");
        }

        Set<String> stepIds = new HashSet<>();
        for (AiSearchPlanStep step : steps) {
            validateStep(step, stepIds);
            stepIds.add(step.getId());
        }
    }

    private void validateStep(AiSearchPlanStep step, Set<String> existingStepIds) {
        if (step == null) {
            throw new IllegalArgumentException("AI 搜索计划步骤不能为空");
        }
        if (step.getId() == null || step.getId().isBlank()) {
            throw new IllegalArgumentException("AI 搜索计划步骤缺少 id");
        }
        if (!existingStepIds.add(step.getId())) {
            throw new IllegalArgumentException("AI 搜索计划步骤 id 重复: " + step.getId());
        }
        if (step.getOperator() == null || step.getOperator().isBlank()) {
            throw new IllegalArgumentException("AI 搜索计划步骤缺少 operator: " + step.getId());
        }
        if (!ALLOWED_OPERATORS.contains(step.getOperator())) {
            throw new IllegalArgumentException("不支持的 AI 搜索算子: " + step.getOperator());
        }
        if (step.getOutputKey() == null || step.getOutputKey().isBlank()) {
            throw new IllegalArgumentException("AI 搜索计划步骤缺少 outputKey: " + step.getId());
        }
        if (step.getDependsOn() == null) {
            return;
        }
        for (String dependency : step.getDependsOn()) {
            if (dependency == null || dependency.isBlank()) {
                throw new IllegalArgumentException("AI 搜索计划存在空依赖: " + step.getId());
            }
            if (!existingStepIds.contains(dependency)) {
                throw new IllegalArgumentException("AI 搜索计划依赖未定义步骤: " + dependency);
            }
        }
    }
}
