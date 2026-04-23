package com.photoexhibition.aisearch.planner;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class BodyChangeAiSearchPlanner {

    public AiSearchPlan plan(String query, Long personId, String personName, int startYear, int endYear) {
        AiSearchPlan plan = new AiSearchPlan();
        plan.setQuery(query);
        plan.setQueryMode("analysis");
        plan.setPlanType("body_change");
        plan.setResultTypes(List.of("photos", "persons"));
        plan.getMetadata().put("intent", "body_change");
        plan.getMetadata().put("personId", personId);
        plan.getMetadata().put("personName", personName);
        plan.getMetadata().put("startYear", startYear);
        plan.getMetadata().put("endYear", endYear);

        AiSearchPlanStep deriveGrowthSignals = new AiSearchPlanStep();
        deriveGrowthSignals.setId("derive_person_growth_signals");
        deriveGrowthSignals.setOperator("derive_person_growth_signals");
        deriveGrowthSignals.setOutputKey("body_change_metrics");
        deriveGrowthSignals.setDescription("根据人物人脸时序数据推导体型变化信号");
        deriveGrowthSignals.setArgs(Map.of(
            "personId", personId,
            "personName", personName == null ? "" : personName,
            "startYear", startYear,
            "endYear", endYear
        ));
        plan.getSteps().add(deriveGrowthSignals);

        return plan;
    }
}
