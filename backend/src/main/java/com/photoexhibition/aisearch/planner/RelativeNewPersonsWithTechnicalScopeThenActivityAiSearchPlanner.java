package com.photoexhibition.aisearch.planner;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RelativeNewPersonsWithTechnicalScopeThenActivityAiSearchPlanner {

    private final RelativeNewPersonsWithTechnicalScopeAiSearchPlanner basePlanner;

    public RelativeNewPersonsWithTechnicalScopeThenActivityAiSearchPlanner(RelativeNewPersonsWithTechnicalScopeAiSearchPlanner basePlanner) {
        this.basePlanner = basePlanner;
    }

    public boolean supports(String query, List<String> cameraCandidates, List<String> lensCandidates) {
        if (!basePlanner.supports(query, cameraCandidates, lensCandidates)) {
            return false;
        }
        return refersToLaterActivity(query);
    }

    public AiSearchPlan plan(String query,
                             List<String> cameraCandidates,
                             List<String> lensCandidates,
                             int offset,
                             int limit) {
        AiSearchPlan plan = basePlanner.plan(query, cameraCandidates, lensCandidates, offset, limit);
        return planForBasePlan(plan, offset, limit);
    }

    public AiSearchPlan planForScope(String query,
                                     int targetYear,
                                     List<String> cameraModels,
                                     List<String> lensModels,
                                     int offset,
                                     int limit) {
        AiSearchPlan plan = basePlanner.planForScope(query, targetYear, cameraModels, lensModels, offset, limit);
        return planForBasePlan(plan, offset, limit);
    }

    private AiSearchPlan planForBasePlan(AiSearchPlan plan, int offset, int limit) {
        plan.setPlanType("relative_new_persons_with_technical_scope_then_activity");
        plan.getMetadata().put("intent", "relative_new_persons_with_technical_scope_then_activity");

        List<AiSearchPlanStep> rewritten = new ArrayList<>();
        for (AiSearchPlanStep step : plan.getSteps()) {
            if ("sorted_filtered_new_persons".equals(step.getId()) || "limited_filtered_new_persons".equals(step.getId())) {
                continue;
            }
            rewritten.add(step);
        }
        plan.setSteps(rewritten);

        AiSearchPlanStep derive = new AiSearchPlanStep();
        derive.setId("followup_active_filtered_new_persons");
        derive.setOperator("derive_temporal_person_activity");
        derive.setInputRef("filtered_new_persons");
        derive.setOutputKey("followup_active_filtered_new_persons");
        derive.getDependsOn().add("filtered_new_persons");
        derive.setDescription("统计器材范围内新认识人物在首次出现后的持续出现频次");
        plan.getSteps().add(derive);

        AiSearchPlanStep sort = new AiSearchPlanStep();
        sort.setId("sorted_followup_active_filtered_new_persons");
        sort.setOperator("sort");
        sort.setInputRef("followup_active_filtered_new_persons");
        sort.setOutputKey("sorted_followup_active_filtered_new_persons");
        sort.getDependsOn().add("followup_active_filtered_new_persons");
        sort.setDescription("按首次出现后的持续出现频次与最近出现时间排序");
        sort.setArgs(java.util.Map.of(
            "field", "matchedPhotoCount",
            "direction", "desc",
            "secondaryField", "matchedLastSeen",
            "secondaryDirection", "desc"
        ));
        plan.getSteps().add(sort);

        AiSearchPlanStep limited = new AiSearchPlanStep();
        limited.setId("limited_followup_active_filtered_new_persons");
        limited.setOperator("limit");
        limited.setInputRef("sorted_followup_active_filtered_new_persons");
        limited.setOutputKey("limited_followup_active_filtered_new_persons");
        limited.getDependsOn().add("sorted_followup_active_filtered_new_persons");
        limited.setDescription("分页截断器材范围内后续最活跃的新认识人物结果");
        limited.setArgs(java.util.Map.of(
            "offset", Math.max(0, offset),
            "size", Math.max(1, limit)
        ));
        plan.getSteps().add(limited);

        return plan;
    }

    private boolean refersToLaterActivity(String query) {
        return query != null && (
            query.contains("后续出现次数最多")
                || query.contains("后来出现次数最多")
                || query.contains("之后出现次数最多")
                || query.contains("后续最活跃")
                || query.contains("后来最活跃")
                || query.contains("之后最活跃")
        );
    }
}
