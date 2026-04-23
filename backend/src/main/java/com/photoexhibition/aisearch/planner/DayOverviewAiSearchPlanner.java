package com.photoexhibition.aisearch.planner;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DayOverviewAiSearchPlanner {

    public AiSearchPlan plan(String query) {
        AiSearchPlan plan = new AiSearchPlan();
        plan.setQuery(query);
        plan.setQueryMode("analysis");
        plan.setPlanType("day_overview");
        plan.setResultTypes(List.of("photos", "albums"));
        plan.getMetadata().put("intent", "day_overview");

        AiSearchPlanStep aggregateDays = new AiSearchPlanStep();
        aggregateDays.setId("aggregate_days");
        aggregateDays.setOperator("aggregate_days");
        aggregateDays.setInputRef("matched_photo_ids");
        aggregateDays.setOutputKey("day_counts");
        aggregateDays.setDescription("对已命中的照片集合按日期聚合计数");
        plan.getSteps().add(aggregateDays);

        return plan;
    }
}
