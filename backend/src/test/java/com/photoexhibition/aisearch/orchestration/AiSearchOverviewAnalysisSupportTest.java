package com.photoexhibition.aisearch.orchestration;

import com.photoexhibition.dto.AiSearchIntent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AiSearchOverviewAnalysisSupportTest {

    private final AiSearchOverviewAnalysisSupport support = new AiSearchOverviewAnalysisSupport();

    @Test
    void shouldCreateScopedAnalysisIntent() {
        AiSearchIntent intent = support.createScopedAnalysisIntent(
            List.of("photos", "albums"),
            "提示",
            "说明"
        );

        assertEquals(List.of("photos", "albums"), intent.getResultTypes());
        assertEquals("提示", intent.getAnswerPrompt());
        assertEquals("说明", intent.getExplanation());
        assertFalse(intent.isIncludeHidden());
    }

    @Test
    void shouldBuildSummaryMetrics() {
        Map<String, Object> metrics = support.buildSummaryMetrics("2025 年", 12L, List.of("杭州(8张)"));

        assertEquals("2025 年", metrics.get("periodLabel"));
        assertEquals(12L, metrics.get("totalMatched"));
        assertEquals(List.of("杭州(8张)"), metrics.get("summaryItems"));
    }

    @Test
    void shouldBuildCountMetrics() {
        Map<String, Object> metrics = support.buildCountMetrics("当前图库中", 20L, 3);

        assertEquals("当前图库中", metrics.get("periodLabel"));
        assertEquals(20L, metrics.get("totalMatched"));
        assertEquals(3, metrics.get("albumSize"));
    }
}
