package com.photoexhibition.aisearch.orchestration;

import com.photoexhibition.aisearch.model.AiSearchPersonAggregate;
import com.photoexhibition.aisearch.model.AiSearchPersonPairAggregate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSearchPersonAnalysisSupportTest {

    private final AiSearchPersonAnalysisSupport support = new AiSearchPersonAnalysisSupport();

    @Test
    void shouldBuildOverviewMetricsAndAnalysisData() {
        AiSearchPersonAggregate first = new AiSearchPersonAggregate();
        first.setPersonId(1L);
        first.setPersonName("小明");
        first.setMatchedPhotoCount(8);
        AiSearchPersonAggregate second = new AiSearchPersonAggregate();
        second.setPersonId(2L);
        second.setPersonName(null);
        second.setMatchedPhotoCount(3);

        Map<String, Object> metrics = support.buildOverviewMetrics("2025 年", List.of(first, second));
        Map<String, Object> analysisData = support.buildOverviewAnalysisData(metrics, "结论");

        assertEquals(2, metrics.get("totalMatched"));
        assertTrue(metrics.containsKey("topPersons"));
        assertEquals("person_overview", analysisData.get("analysisType"));
        assertEquals("结论", analysisData.get("conclusion"));
    }

    @Test
    void shouldBuildCooccurrenceMetricsAndAnalysisData() {
        AiSearchPersonAggregate aggregate = new AiSearchPersonAggregate();
        aggregate.setPersonId(3L);
        aggregate.setPersonName("小红");
        aggregate.setMatchedPhotoCount(6);

        Map<String, Object> metrics = support.buildCooccurrenceMetrics("2025 年", "小明", 12L, List.of(aggregate));
        Map<String, Object> analysisData = support.buildCooccurrenceAnalysisData(metrics, "同框较多");

        assertEquals("小明", metrics.get("anchorPersonName"));
        assertEquals(12L, metrics.get("photoMatched"));
        assertEquals("person_cooccurrence", analysisData.get("analysisType"));
        assertEquals("小明", analysisData.get("anchorPersonName"));
    }

    @Test
    void shouldBuildPairMetricsAndAnalysisData() {
        AiSearchPersonPairAggregate pair = new AiSearchPersonPairAggregate();
        pair.setPersonAId(1L);
        pair.setPersonAName("小明");
        pair.setPersonBId(2L);
        pair.setPersonBName("小红");
        pair.setMatchedPhotoCount(9);

        Map<String, Object> metrics = support.buildPairCooccurrenceMetrics("2025 年", 20L, List.of(pair));
        Map<String, Object> analysisData = support.buildPairCooccurrenceAnalysisData(metrics, "组合最高频");

        assertEquals(1, metrics.get("totalMatched"));
        assertEquals("person_pair_cooccurrence", analysisData.get("analysisType"));
        assertEquals(20L, analysisData.get("photoMatched"));
    }
}
