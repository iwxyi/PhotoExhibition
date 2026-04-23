package com.photoexhibition.aisearch.planner;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ThemeOverviewAiSearchPlanner {

    public AiSearchPlan plan(String query) {
        AiSearchPlan plan = new AiSearchPlan();
        plan.setQuery(query);
        plan.setQueryMode("analysis");
        plan.setPlanType("theme_overview");
        plan.setResultTypes(List.of("photos", "albums"));
        plan.getMetadata().put("intent", "theme_overview");

        AiSearchPlanStep aggregateThemes = new AiSearchPlanStep();
        aggregateThemes.setId("aggregate_themes");
        aggregateThemes.setOperator("aggregate_themes");
        aggregateThemes.setInputRef("matched_photo_ids");
        aggregateThemes.setOutputKey("theme_counts");
        aggregateThemes.setDescription("对已命中的照片集合按主题信号聚合计数");
        plan.getSteps().add(aggregateThemes);

        return plan;
    }
}
