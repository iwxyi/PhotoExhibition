package com.photoexhibition.aisearch.reducer;

import com.photoexhibition.aisearch.executor.AiSearchExecutionResult;
import com.photoexhibition.aisearch.plan.AiSearchPlan;

public interface AiSearchEvidenceReducer {
    AiSearchEvidenceBundle reduce(AiSearchPlan plan, AiSearchExecutionResult executionResult);
}
