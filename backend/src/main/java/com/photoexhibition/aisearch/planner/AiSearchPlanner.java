package com.photoexhibition.aisearch.planner;

import com.photoexhibition.aisearch.plan.AiSearchPlan;

public interface AiSearchPlanner {
    AiSearchPlan plan(AiSearchPlannerRequest request);
}
