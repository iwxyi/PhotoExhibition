package com.photoexhibition.aisearch.planner;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LocationOverviewAiSearchPlanner {

    public AiSearchPlan plan(String query) {
        AiSearchPlan plan = new AiSearchPlan();
        plan.setQuery(query);
        plan.setQueryMode("analysis");
        plan.setPlanType("location_overview");
        plan.setResultTypes(List.of("photos", "albums"));
        plan.getMetadata().put("intent", "location_overview");

        AiSearchPlanStep aggregateLocations = new AiSearchPlanStep();
        aggregateLocations.setId("aggregate_locations");
        aggregateLocations.setOperator("aggregate_locations");
        aggregateLocations.setInputRef("matched_photo_ids");
        aggregateLocations.setOutputKey("location_counts");
        aggregateLocations.setDescription("对已命中的照片集合按地点信号聚合计数");
        plan.getSteps().add(aggregateLocations);

        return plan;
    }
}
