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

class AiSearchStructuredPhotoResponseSupportTest {

    private final AiSearchStructuredPhotoResponseSupport support = new AiSearchStructuredPhotoResponseSupport();

    @Test
    void shouldEnsureStructuredMatchedPhotoIntentDefaults() {
        AiSearchIntent intent = support.ensureStructuredMatchedPhotoIntentDefaults(null, "photos", "说明");

        assertEquals(List.of("photos"), intent.getResultTypes());
        assertEquals("说明", intent.getExplanation());
        assertNotNull(intent.getMust());
        assertNotNull(intent.getShould());
        assertNotNull(intent.getMustNot());
    }

    @Test
    void shouldBuildStructuredMatchedPhotoResponsesByPlanType() {
        AiSearchPlan personPlan = new AiSearchPlan();
        personPlan.setPlanType("person_overview");
        personPlan.setQueryMode("analysis");
        AiSearchPlan themePlan = new AiSearchPlan();
        themePlan.setPlanType("theme_overview");
        themePlan.setQueryMode("analysis");
        AiSearchPlan yearPlan = new AiSearchPlan();
        yearPlan.setPlanType("year_compare");
        yearPlan.setQueryMode("analysis");

        AiSearchIntent intent = new AiSearchIntent();
        AiSearchExecutionResult executionResult = new AiSearchExecutionResult();
        AiSearchService.PhotoSearchExecution photoSearch = new AiSearchService.PhotoSearchExecution(List.of(), List.of(), 12L);
        List<AlbumDTO> albums = List.of(new AlbumDTO());

        AiSearchResponse personResponse = support.buildStructuredMatchedPhotoAnalysisResponse(
            personPlan,
            intent,
            photoSearch,
            albums,
            executionResult,
            this::buildResponse,
            (result, outputKey) -> List.of(personSummary()),
            (result, outputKey) -> 3L,
            (result, outputKey) -> 2L,
            (result, answer) -> Map.of("type", "person"),
            (result, answer) -> Map.of("type", "cooccurrence"),
            (result, answer) -> Map.of("type", "pair"),
            (currentIntent, plan, page, size) -> {
                AiSearchResponse response = new AiSearchResponse();
                response.setAnswer("year");
                return response;
            },
            0,
            10
        );

        AiSearchResponse themeResponse = support.buildStructuredMatchedPhotoAnalysisResponse(
            themePlan,
            intent,
            photoSearch,
            albums,
            executionResult,
            this::buildResponse,
            (result, outputKey) -> List.of(personSummary()),
            (result, outputKey) -> 3L,
            (result, outputKey) -> 2L,
            (result, answer) -> Map.of("type", "person"),
            (result, answer) -> Map.of("type", "cooccurrence"),
            (result, answer) -> Map.of("type", "pair"),
            (currentIntent, plan, page, size) -> new AiSearchResponse(),
            0,
            10
        );

        AiSearchResponse yearResponse = support.buildStructuredMatchedPhotoAnalysisResponse(
            yearPlan,
            intent,
            photoSearch,
            albums,
            executionResult,
            this::buildResponse,
            (result, outputKey) -> List.of(personSummary()),
            (result, outputKey) -> 3L,
            (result, outputKey) -> 2L,
            (result, answer) -> Map.of("type", "person"),
            (result, answer) -> Map.of("type", "cooccurrence"),
            (result, answer) -> Map.of("type", "pair"),
            (currentIntent, plan, page, size) -> {
                AiSearchResponse response = new AiSearchResponse();
                response.setAnswer("year");
                return response;
            },
            0,
            10
        );

        assertEquals(1, personResponse.getPersons().size());
        assertEquals("person", ((Map<?, ?>) personResponse.getAnalysisData()).get("type"));
        assertEquals(12L, themeResponse.getTotalElements());
        assertEquals("year", yearResponse.getAnswer());
    }

    private AiSearchResponse buildResponse(String queryMode,
                                           AiSearchIntent intent,
                                           AiSearchService.PhotoSearchExecution photoSearch,
                                           List<AlbumDTO> albums,
                                           List<PersonSummaryDTO> persons,
                                           long totalElements,
                                           AiSearchPlan plan,
                                           AiSearchExecutionResult executionResult) {
        AiSearchResponse response = new AiSearchResponse();
        response.setQueryMode(queryMode);
        response.setAlbums(albums);
        response.setPersons(persons);
        response.setTotalElements(totalElements);
        response.setAnswer("ok");
        return response;
    }

    private PersonSummaryDTO personSummary() {
        PersonSummaryDTO dto = new PersonSummaryDTO();
        dto.setId(1L);
        dto.setName("小明");
        return dto;
    }
}
