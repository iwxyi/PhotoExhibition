package com.photoexhibition.aisearch.operator;

import com.photoexhibition.aisearch.executor.AiSearchExecutionContext;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;

public interface AiSearchOperator {
    String getName();

    Object execute(AiSearchPlanStep step, AiSearchExecutionContext context);
}
