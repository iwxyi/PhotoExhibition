package com.photoexhibition.aisearch.planner;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TagOverviewAiSearchPlanner {

    public AiSearchPlan plan(String query) {
        AiSearchPlan plan = new AiSearchPlan();
        plan.setQuery(query);
        plan.setQueryMode("analysis");
        plan.setPlanType("tag_overview");
        plan.setResultTypes(List.of("photos", "albums"));
        plan.getMetadata().put("intent", "tag_overview");

        AiSearchPlanStep aggregateTags = new AiSearchPlanStep();
        aggregateTags.setId("aggregate_tags");
        aggregateTags.setOperator("aggregate_tags");
        aggregateTags.setInputRef("matched_photo_ids");
        aggregateTags.setOutputKey("tag_counts");
        aggregateTags.setDescription("对已命中的照片集合按标签聚合计数");
        plan.getSteps().add(aggregateTags);

        return plan;
    }
}
