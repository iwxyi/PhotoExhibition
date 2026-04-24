package com.photoexhibition.aisearch.orchestration;

import com.photoexhibition.aisearch.executor.AiSearchExecutionContext;
import com.photoexhibition.aisearch.executor.AiSearchExecutionResult;
import com.photoexhibition.aisearch.executor.AiSearchPlanExecutor;
import com.photoexhibition.aisearch.plan.AiSearchPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiSearchYearCompareSupportTest {

    private final AiSearchYearCompareSupport support = new AiSearchYearCompareSupport();

    @Test
    void shouldResolveRelativeYearComparison() {
        AiSearchYearCompareSupport.YearComparison comparison = support.resolveComparison("去年和前年相比樱花更多还是更少", 2026);

        assertEquals(2025, comparison.getLeftYear());
        assertEquals(2024, comparison.getRightYear());
    }

    @Test
    void shouldAttachYearCompareMetricsAfterExecution() {
        AiSearchPlanExecutor executor = mock(AiSearchPlanExecutor.class);
        AiSearchPlan plan = new AiSearchPlan();
        plan.setQuery("test");
        plan.setQueryMode("analysis");
        plan.getMetadata().put("leftYear", 2025);
        plan.getMetadata().put("rightYear", 2024);
        plan.getMetadata().put("subject", "樱花");

        AiSearchExecutionResult executionResult = new AiSearchExecutionResult();
        executionResult.getFinalOutputs().put("period_comparison", Map.of(
            "leftCount", 12L,
            "rightCount", 8L,
            "difference", 4L,
            "trend", "increase"
        ));
        when(executor.execute(any(AiSearchPlan.class), any(AiSearchExecutionContext.class))).thenReturn(executionResult);

        AiSearchExecutionResult result = support.executePlan(executor, plan, List.of(1L, 2L), List.of(3L));
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) result.getFinalOutputs().get("year_compare_metrics");

        assertEquals(2025, metrics.get("leftYear"));
        assertEquals(2024, metrics.get("rightYear"));
        assertEquals("樱花", metrics.get("subject"));
        assertEquals(12L, metrics.get("leftCount"));
    }

    @Test
    void shouldBuildYearCompareAnalysisData() {
        AiSearchExecutionResult executionResult = new AiSearchExecutionResult();
        executionResult.getFinalOutputs().put("year_compare_metrics", Map.of(
            "leftCount", 12L,
            "rightCount", 8L,
            "difference", 4L,
            "changeRatio", 0.5D,
            "subject", "樱花",
            "trend", "increase"
        ));

        Map<String, Object> analysisData = support.buildAnalysisData(
            new AiSearchYearCompareSupport.YearComparison(2025, 2024),
            executionResult,
            "结论"
        );

        assertEquals("year_compare", analysisData.get("analysisType"));
        assertEquals(2025, analysisData.get("leftYear"));
        assertEquals(2024, analysisData.get("rightYear"));
        assertEquals("樱花", analysisData.get("subject"));
        assertTrue(analysisData.containsKey("conclusion"));
    }
}
