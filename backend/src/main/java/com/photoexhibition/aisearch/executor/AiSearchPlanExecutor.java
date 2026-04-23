package com.photoexhibition.aisearch.executor;

import com.photoexhibition.aisearch.plan.AiSearchPlan;

public interface AiSearchPlanExecutor {
    AiSearchExecutionResult execute(AiSearchPlan plan, AiSearchExecutionContext context);
}
