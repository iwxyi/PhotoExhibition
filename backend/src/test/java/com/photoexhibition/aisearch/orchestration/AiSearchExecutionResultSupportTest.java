package com.photoexhibition.aisearch.orchestration;

import com.photoexhibition.aisearch.executor.AiSearchExecutionContext;
import com.photoexhibition.aisearch.executor.AiSearchExecutionResult;
import com.photoexhibition.aisearch.executor.AiSearchPlanExecutor;
import com.photoexhibition.aisearch.model.AiSearchPersonAggregate;
import com.photoexhibition.aisearch.model.AiSearchPersonGrowthAggregate;
import com.photoexhibition.aisearch.model.AiSearchPersonPairAggregate;
import com.photoexhibition.aisearch.plan.AiSearchPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiSearchExecutionResultSupportTest {

    private final AiSearchExecutionResultSupport support = new AiSearchExecutionResultSupport();

    @Test
    void shouldExecuteMatchedPhotoOverviewPlanAndAttachMetrics() {
        AiSearchPlanExecutor executor = mock(AiSearchPlanExecutor.class);
        AiSearchPlan plan = new AiSearchPlan();
        plan.setQueryMode("analysis");
        AiSearchExecutionResult executionResult = new AiSearchExecutionResult();
        when(executor.execute(any(AiSearchPlan.class), any(AiSearchExecutionContext.class))).thenReturn(executionResult);

        AiSearchExecutionResult result = support.executeMatchedPhotoOverviewPlan(
            executor,
            "查询",
            plan,
            List.of(1L, 2L),
            "metrics",
            current -> Map.of("totalMatched", 2)
        );

        assertEquals(2, support.extractMetrics(result, "metrics").get("totalMatched"));
    }

    @Test
    void shouldExtractTypedAggregates() {
        AiSearchExecutionResult result = new AiSearchExecutionResult();
        AiSearchPersonAggregate person = new AiSearchPersonAggregate();
        AiSearchPersonPairAggregate pair = new AiSearchPersonPairAggregate();
        AiSearchPersonGrowthAggregate growth = new AiSearchPersonGrowthAggregate();
        result.getFinalOutputs().put("persons", List.of(person, "x"));
        result.getFinalOutputs().put("pairs", List.of(pair));
        result.getFinalOutputs().put("growth", List.of(growth));

        assertEquals(1, support.extractPersonAggregates(result, "persons").size());
        assertEquals(1, support.extractPersonPairAggregates(result, "pairs").size());
        assertEquals(1, support.extractPersonGrowthAggregates(result, "growth").size());
    }

    @Test
    void shouldReturnEmptyCollectionsForMissingOutputs() {
        AiSearchExecutionResult result = new AiSearchExecutionResult();

        assertTrue(support.extractMetrics(result, "missing").isEmpty());
        assertTrue(support.extractPersonAggregates(result, "missing").isEmpty());
        assertTrue(support.extractPersonPairAggregates(result, "missing").isEmpty());
        assertTrue(support.extractPersonGrowthAggregates(result, "missing").isEmpty());
    }
}
