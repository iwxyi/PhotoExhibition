package com.photoexhibition.aisearch.planner;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CountOverviewAiSearchPlanner {

    public AiSearchPlan plan(String query) {
        AiSearchPlan plan = new AiSearchPlan();
        plan.setQuery(query);
        plan.setQueryMode("analysis");
        plan.setPlanType("count_overview");
        plan.setResultTypes(List.of("photos", "albums"));
        plan.getMetadata().put("intent", "count_overview");

        AiSearchPlanStep aggregateAlbums = new AiSearchPlanStep();
        aggregateAlbums.setId("aggregate_albums");
        aggregateAlbums.setOperator("aggregate_albums");
        aggregateAlbums.setInputRef("matched_photo_ids");
        aggregateAlbums.setOutputKey("album_counts");
        aggregateAlbums.setDescription("对已命中的照片集合按相册聚合计数");
        plan.getSteps().add(aggregateAlbums);

        return plan;
    }
}
