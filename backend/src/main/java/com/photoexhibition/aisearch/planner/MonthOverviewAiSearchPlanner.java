package com.photoexhibition.aisearch.planner;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MonthOverviewAiSearchPlanner {

    public AiSearchPlan plan(String query) {
        AiSearchPlan plan = new AiSearchPlan();
        plan.setQuery(query);
        plan.setQueryMode("analysis");
        plan.setPlanType("month_overview");
        plan.setResultTypes(List.of("photos", "albums"));
        plan.getMetadata().put("intent", "month_overview");

        AiSearchPlanStep aggregateMonths = new AiSearchPlanStep();
        aggregateMonths.setId("aggregate_months");
        aggregateMonths.setOperator("aggregate_months");
        aggregateMonths.setInputRef("matched_photo_ids");
        aggregateMonths.setOutputKey("month_counts");
        aggregateMonths.setDescription("对已命中的照片集合按月份聚合计数");
        plan.getSteps().add(aggregateMonths);

        return plan;
    }
}
