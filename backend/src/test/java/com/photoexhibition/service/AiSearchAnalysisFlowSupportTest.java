package com.photoexhibition.service;

import com.photoexhibition.aisearch.executor.AiSearchExecutionResult;
import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.dto.AiSearchIntent;
import com.photoexhibition.dto.AiSearchResponse;
import com.photoexhibition.dto.AlbumDTO;
import com.photoexhibition.dto.PersonSummaryDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AiSearchAnalysisFlowSupportTest {

    private final AiSearchAnalysisFlowSupport support = new AiSearchAnalysisFlowSupport();

    @Test
    void shouldBuildMatchedPhotoOverviewAnalysisResponse() {
        AiSearchResponse response = support.buildMatchedPhotoOverviewAnalysisResponse(
            "查询",
            0,
            10,
            "analysis",
            AiSearchIntent::new,
            AiSearchPlan::new,
            "metrics",
            (intent, photoSearch, result) -> Map.of("totalMatched", 1),
            (photoSearch, result) -> List.of(new AlbumDTO()),
            (query, intent, applyRelativeYearRange) -> intent.setExplanation("说明"),
            (intent, page, size) -> AiSearchService.PhotoSearchExecution.empty(),
            (query, plan, photoSearch, metricsKey, metricsBuilder) -> {
                AiSearchExecutionResult result = new AiSearchExecutionResult();
                result.getFinalOutputs().put(metricsKey, metricsBuilder.apply(result));
                return result;
            },
            (queryMode, intent, photoSearch, albums, persons, totalElements, plan, executionResult) -> {
                AiSearchResponse built = new AiSearchResponse();
                built.setQueryMode(queryMode);
                built.setExplanation(intent.getExplanation());
                built.setAlbums(albums);
                return built;
            }
        );

        assertEquals("analysis", response.getQueryMode());
        assertEquals("说明", response.getExplanation());
        assertEquals(1, response.getAlbums().size());
    }

    @Test
    void shouldBuildPersonAggregateAndPairResponses() {
        AiSearchIntent intent = new AiSearchIntent();
        AiSearchPlan plan = new AiSearchPlan();
        AiSearchExecutionResult result = new AiSearchExecutionResult();
        AiSearchService.PhotoSearchExecution photoSearch = AiSearchService.PhotoSearchExecution.empty();

        AiSearchResponse aggregateResponse = support.buildPersonAggregateAnalysisResponse(
            "analysis",
            intent,
            photoSearch,
            plan,
            result,
            "limited",
            "sorted",
            current -> List.of(new AlbumDTO()),
            (executionResult, outputKey) -> List.of(new PersonSummaryDTO()),
            (executionResult, outputKey) -> 5L,
            (queryMode, currentIntent, currentPhotoSearch, albums, persons, totalElements, currentPlan, executionResult) -> {
                AiSearchResponse response = new AiSearchResponse();
                response.setPersons(persons);
                response.setTotalElements(totalElements);
                return response;
            },
            response -> response.setExplanation("aggregate")
        );

        AiSearchResponse pairResponse = support.buildPersonPairAnalysisResponse(
            "analysis",
            intent,
            photoSearch,
            plan,
            result,
            "pairs",
            current -> List.of(new AlbumDTO()),
            (executionResult, outputKey) -> 3L,
            (queryMode, currentIntent, currentPhotoSearch, albums, persons, totalElements, currentPlan, executionResult) -> {
                AiSearchResponse response = new AiSearchResponse();
                response.setTotalElements(totalElements);
                return response;
            },
            response -> response.setExplanation("pair")
        );

        assertEquals(1, aggregateResponse.getPersons().size());
        assertEquals(5L, aggregateResponse.getTotalElements());
        assertEquals("aggregate", aggregateResponse.getExplanation());
        assertEquals(3L, pairResponse.getTotalElements());
        assertEquals("pair", pairResponse.getExplanation());
        assertNotNull(pairResponse);
    }
}
