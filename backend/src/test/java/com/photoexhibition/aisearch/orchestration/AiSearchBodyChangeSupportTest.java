package com.photoexhibition.aisearch.orchestration;

import com.photoexhibition.aisearch.executor.AiSearchExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSearchBodyChangeSupportTest {

    private final AiSearchBodyChangeSupport support = new AiSearchBodyChangeSupport();

    @Test
    void shouldAnalyzeBodyChangeWithExplicitYears() {
        AiSearchBodyChangeSupport.BodyChangeAnalysis analysis = support.analyze("看下小明 2023年到2025年是不是胖了", 1L, "小明", 2026);

        assertEquals(1L, analysis.getPersonId());
        assertEquals("小明", analysis.getPersonName());
        assertEquals(2023, analysis.getStartYear());
        assertEquals(2025, analysis.getEndYear());
    }

    @Test
    void shouldAnalyzeBodyChangeWithRelativeYearCue() {
        AiSearchBodyChangeSupport.BodyChangeAnalysis analysis = support.analyze("小明去年胖了吗", 1L, "小明", 2026);

        assertEquals(2025, analysis.getStartYear());
        assertEquals(2025, analysis.getEndYear());
        assertTrue(analysis.getExplanation().contains("小明"));
    }

    @Test
    void shouldBuildBodyChangeAnalysisData() {
        AiSearchExecutionResult executionResult = new AiSearchExecutionResult();
        executionResult.getFinalOutputs().put("body_change_metrics", Map.of(
            "totalPhotos", 18L,
            "avgFaceArea", 0.42D,
            "avgFaceWidth", 120.5D,
            "avgFaceHeight", 140.0D,
            "avgAspectRatio", 0.86D,
            "yearlyStats", List.of(Map.of("year", 2025, "avgFaceArea", 0.45D)),
            "trend", "gain",
            "changePercent", 12.5D,
            "firstPeriod", "2024",
            "lastPeriod", "2025"
        ));

        AiSearchBodyChangeSupport.BodyChangeAnalysis analysis = support.analyze("小明去年胖了吗", 1L, "小明", 2026);
        Map<String, Object> analysisData = support.buildAnalysisData(analysis, executionResult, "明显变胖");

        assertEquals("body_change", analysisData.get("analysisType"));
        assertEquals("小明", analysisData.get("personName"));
        assertEquals(12.5D, analysisData.get("changePercent"));
        assertEquals("明显变胖", analysisData.get("conclusion"));
    }
}
