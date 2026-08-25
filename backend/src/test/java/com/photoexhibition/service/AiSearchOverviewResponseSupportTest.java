package com.photoexhibition.service;

import com.photoexhibition.aisearch.executor.AiSearchExecutionResult;
import com.photoexhibition.aisearch.orchestration.AiSearchOverviewAnalysisSupport;
import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.dto.AiSearchIntent;
import com.photoexhibition.dto.AiSearchResponse;
import com.photoexhibition.dto.AlbumDTO;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiSearchOverviewResponseSupportTest {

    private final AiSearchOverviewResponseSupport support = new AiSearchOverviewResponseSupport(
        new AiSearchOverviewAnalysisSupport(),
        new AiSearchAnalysisFlowSupport()
    );

    @Test
    void shouldBuildSummaryOverviewResponseMetrics() {
        AiSearchResponse response = support.buildSummaryOverviewResponse(
            "查询",
            0,
            10,
            "analysis",
            AiSearchIntent::new,
            AiSearchPlan::new,
            "metrics",
            result -> linkedStringCounts("杭州", 8L, "上海", 3L),
            2,
            (photoSearch, result) -> List.of(new AlbumDTO()),
            (query, intent, applyRelativeYearRange) -> { },
            (intent, page, size) -> new AiSearchService.PhotoSearchExecution(List.of(), List.of(), 11L),
            (query, plan, photoSearch, metricsKey, metricsBuilder) -> {
                AiSearchExecutionResult result = new AiSearchExecutionResult();
                result.getFinalOutputs().put(metricsKey, metricsBuilder.apply(result));
                return result;
            },
            (queryMode, intent, photoSearch, albums, persons, totalElements, plan, executionResult) -> {
                AiSearchResponse built = new AiSearchResponse();
                built.setAnalysisData((Map<String, Object>) executionResult.getFinalOutputs().get("metrics"));
                built.setAlbums(albums);
                return built;
            },
            intent -> "2025 年"
        );

        assertEquals(List.of("杭州(8张)", "上海(3张)"), response.getAnalysisData().get("summaryItems"));
        assertEquals(11L, response.getAnalysisData().get("totalMatched"));
        assertEquals(1, response.getAlbums().size());
    }

    @Test
    void shouldBuildAlbumOverviewAndCountMetrics() {
        AiSearchResponse albumResponse = support.buildAlbumOverviewResponse(
            "查询",
            0,
            10,
            "analysis",
            AiSearchIntent::new,
            AiSearchPlan::new,
            "album_metrics",
            result -> linkedAlbumCounts(1L, 9L, 2L, 4L),
            this::fetchAlbumsByCount,
            6,
            2,
            (query, intent, applyRelativeYearRange) -> { },
            (intent, page, size) -> new AiSearchService.PhotoSearchExecution(List.of(), List.of(), 13L),
            (query, plan, photoSearch, metricsKey, metricsBuilder) -> {
                AiSearchExecutionResult result = new AiSearchExecutionResult();
                result.getFinalOutputs().put(metricsKey, metricsBuilder.apply(result));
                return result;
            },
            (queryMode, intent, photoSearch, albums, persons, totalElements, plan, executionResult) -> {
                AiSearchResponse built = new AiSearchResponse();
                built.setAnalysisData((Map<String, Object>) executionResult.getFinalOutputs().get("album_metrics"));
                built.setAlbums(albums);
                return built;
            },
            intent -> "今年",
            id -> "相册#" + id
        );

        AiSearchResponse countResponse = support.buildCountOverviewResponse(
            "查询",
            0,
            10,
            "analysis",
            AiSearchIntent::new,
            AiSearchPlan::new,
            "count_metrics",
            result -> linkedAlbumCounts(1L, 9L, 2L, 4L, 3L, 1L),
            this::fetchAlbumsByCount,
            4,
            (query, intent, applyRelativeYearRange) -> { },
            (intent, page, size) -> new AiSearchService.PhotoSearchExecution(List.of(), List.of(), 14L),
            (query, plan, photoSearch, metricsKey, metricsBuilder) -> {
                AiSearchExecutionResult result = new AiSearchExecutionResult();
                result.getFinalOutputs().put(metricsKey, metricsBuilder.apply(result));
                return result;
            },
            (queryMode, intent, photoSearch, albums, persons, totalElements, plan, executionResult) -> {
                AiSearchResponse built = new AiSearchResponse();
                built.setAnalysisData((Map<String, Object>) executionResult.getFinalOutputs().get("count_metrics"));
                built.setAlbums(albums);
                return built;
            },
            intent -> "当前图库中"
        );

        assertEquals(List.of("旅行(9张)", "人像(4张)"), albumResponse.getAnalysisData().get("summaryItems"));
        assertEquals(3, countResponse.getAnalysisData().get("albumSize"));
        assertEquals(3, countResponse.getAlbums().size());
    }

    private Map<String, Long> linkedStringCounts(Object... values) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            counts.put((String) values[i], (Long) values[i + 1]);
        }
        return counts;
    }

    private Map<Long, Long> linkedAlbumCounts(Object... values) {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            counts.put((Long) values[i], (Long) values[i + 1]);
        }
        return counts;
    }

    private List<AlbumDTO> fetchAlbumsByCount(Map<Long, Long> counts, int limit) {
        return counts.keySet().stream()
            .limit(limit)
            .map(id -> {
                AlbumDTO album = new AlbumDTO();
                album.setId(id);
                album.setName(id == 1L ? "旅行" : id == 2L ? "人像" : "相册#" + id);
                return album;
            })
            .collect(Collectors.toList());
    }
}
