package com.photoexhibition.service;

import com.photoexhibition.aisearch.executor.AiSearchExecutionResult;
import com.photoexhibition.aisearch.model.AiSearchPersonAggregate;
import com.photoexhibition.aisearch.model.AiSearchPersonGrowthAggregate;
import com.photoexhibition.aisearch.model.AiSearchPersonPairAggregate;
import com.photoexhibition.aisearch.orchestration.AiSearchExecutionResultSupport;
import com.photoexhibition.aisearch.orchestration.AiSearchPersonAnalysisSupport;
import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.reducer.AiSearchEvidenceBundle;
import com.photoexhibition.dto.AiSearchIntent;
import com.photoexhibition.dto.AiSearchResponse;
import com.photoexhibition.dto.AlbumDTO;
import com.photoexhibition.dto.PersonSummaryDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiSearchPersonResponseSupportTest {

    private final AiSearchPersonResponseSupport support = new AiSearchPersonResponseSupport(
        new AiSearchPersonAnalysisSupport(),
        new AiSearchAnalysisFlowSupport(),
        new AiSearchExecutionResultSupport()
    );

    @Test
    void shouldBuildPersonOverviewResponse() {
        AiSearchResponse response = support.buildPersonOverviewResponse(
            "查询",
            0,
            10,
            "analysis",
            AiSearchIntent::new,
            AiSearchPlan::new,
            (query, intent, applyRelativeYearRange) -> { },
            (intent, page, size) -> new AiSearchService.PhotoSearchExecution(List.of(), List.of(), 7L),
            (query, plan, photoSearch, metricsKey, metricsBuilder) -> {
                AiSearchExecutionResult result = new AiSearchExecutionResult();
                result.getFinalOutputs().put("sorted_persons", List.of(personAggregate(1L, "小明", 7)));
                result.getFinalOutputs().put("limited_persons", List.of(personAggregate(1L, "小明", 7)));
                result.getFinalOutputs().put(metricsKey, metricsBuilder.apply(result));
                return result;
            },
            (queryMode, intent, photoSearch, albums, persons, totalElements, plan, executionResult) -> {
                AiSearchResponse built = new AiSearchResponse();
                built.setPersons(persons);
                built.setTotalElements(totalElements);
                built.setAnswer("概览");
                return built;
            },
            intent -> "今年",
            photoSearch -> List.of(new AlbumDTO()),
            (executionResult, outputKey) -> List.of(personSummary(1L, "小明")),
            this::extractPersonAggregates
        );

        assertEquals(1L, response.getTotalElements());
        assertEquals(1, response.getPersons().size());
        assertEquals("person_overview", response.getAnalysisData().get("analysisType"));
    }

    @Test
    void shouldBuildEmptyCooccurrenceWhenAnchorMissing() {
        AiSearchResponse response = support.buildPersonCooccurrenceResponse(
            "查询",
            0,
            10,
            "analysis",
            AiSearchIntent::new,
            (query, intent, applyRelativeYearRange) -> { },
            (intent, page, size) -> AiSearchService.PhotoSearchExecution.empty(),
            (query, anchorPersonId, offset, size) -> new AiSearchPlan(),
            (query, plan, photoSearch, metricsKey, metricsBuilder) -> new AiSearchExecutionResult(),
            (queryMode, intent, photoSearch, albums, persons, totalElements, plan, executionResult) -> new AiSearchResponse(),
            intent -> "今年",
            photoSearch -> List.of(),
            (executionResult, outputKey) -> List.of(),
            this::extractPersonAggregates,
            personId -> "人物#" + personId,
            (queryMode, intent, explanation, answer) -> {
                AiSearchResponse built = new AiSearchResponse();
                built.setExplanation(explanation);
                built.setAnswer(answer);
                return built;
            }
        );

        assertEquals("未能识别人物共现分析中的锚点人物", response.getExplanation());
        assertEquals("检索结论：未识别出要分析的人物，暂时无法判断共同出现关系。", response.getAnswer());
    }

    @Test
    void shouldBuildPairAnalysisData() {
        AiSearchExecutionResult result = new AiSearchExecutionResult();
        result.getFinalOutputs().put("sorted_cooccurring_pairs", List.of(pairAggregate(1L, "小明", 2L, "小红", 5)));
        result.getFinalOutputs().put("person_pair_cooccurrence_metrics", support.buildPairCooccurrenceMetrics(
            new AiSearchIntent(),
            new AiSearchService.PhotoSearchExecution(List.of(), List.of(), 9L),
            result,
            intent -> "今年",
            this::extractPairAggregates
        ));

        Map<String, Object> analysisData = support.buildPairCooccurrenceAnalysisData(result, "组合");

        assertEquals("person_pair_cooccurrence", analysisData.get("analysisType"));
        assertEquals(9L, analysisData.get("photoMatched"));
        assertEquals(1, ((List<?>) analysisData.get("topPairs")).size());
    }

    @Test
    void shouldBuildStructuredPersonResponses() {
        AiSearchPlan plan = new AiSearchPlan();
        plan.setQueryMode("analysis");
        plan.setMetadata(Map.of("scopeKeywords", List.of("杭州")));

        AiSearchResponse aggregateResponse = support.buildStructuredPersonAggregateResponse(
            "原始查询",
            null,
            plan,
            "all",
            "paged",
            "persons",
            "默认说明",
            (originalQuery, currentPlan) -> {
                AiSearchExecutionResult result = new AiSearchExecutionResult();
                result.getFinalOutputs().put("all", List.of(personAggregate(1L, "小明", 7)));
                result.getFinalOutputs().put("paged", List.of(personAggregate(1L, "小明", 7)));
                return result;
            },
            this::extractPersonAggregates,
            aggregate -> personSummary(aggregate.getPersonId(), aggregate.getPersonName()),
            (currentPlan, executionResult) -> new AiSearchEvidenceBundle(),
            evidenceBundle -> "结构化人物",
            (currentPlan, executionResult, evidenceBundle, includeEvidence) -> Map.of("ok", true),
            (intent, currentPlan, executionResult) ->
                intent.setKeywords(new ArrayList<>((List<String>) currentPlan.getMetadata().get("scopeKeywords"))),
            (response, executionResult) -> response.setAnalysisData(Map.of("type", "aggregate"))
        );

        AiSearchResponse pairResponse = support.buildStructuredPersonPairResponse(
            "原始查询",
            null,
            plan,
            "pairs",
            "albums",
            "组合说明",
            (originalQuery, currentPlan) -> {
                AiSearchExecutionResult result = new AiSearchExecutionResult();
                result.getFinalOutputs().put("pairs", List.of(pairAggregate(1L, "小明", 2L, "小红", 5)));
                return result;
            },
            this::extractPairAggregates,
            (currentPlan, executionResult) -> new AiSearchEvidenceBundle(),
            evidenceBundle -> "结构化组合",
            (currentPlan, executionResult, evidenceBundle, includeEvidence) -> Map.of("ok", true),
            (intent, currentPlan, executionResult) -> intent.setKeywords(List.of("杭州")),
            (response, executionResult) -> response.setAnalysisData(Map.of("type", "pair"))
        );

        AiSearchResponse growthResponse = support.buildStructuredPersonGrowthResponse(
            "原始查询",
            null,
            plan,
            "growth_all",
            "growth_paged",
            "persons",
            "变化说明",
            (originalQuery, currentPlan) -> {
                AiSearchExecutionResult result = new AiSearchExecutionResult();
                result.getFinalOutputs().put("growth_all", List.of(growthAggregate(1L, "小明")));
                result.getFinalOutputs().put("growth_paged", List.of(growthAggregate(1L, "小明")));
                return result;
            },
            this::extractGrowthAggregates,
            aggregate -> personSummary(aggregate.getPersonId(), aggregate.getPersonName()),
            (currentPlan, executionResult) -> new AiSearchEvidenceBundle(),
            evidenceBundle -> "结构化变化",
            (currentPlan, executionResult, evidenceBundle, includeEvidence) -> Map.of("ok", true),
            null,
            (response, executionResult) -> response.setAnalysisData(Map.of("type", "growth"))
        );

        assertEquals(1L, aggregateResponse.getTotalElements());
        assertEquals(1, aggregateResponse.getPersons().size());
        assertEquals("默认说明", aggregateResponse.getExplanation());
        assertEquals(List.of("杭州"), aggregateResponse.getParsedIntent().getKeywords());
        assertEquals("aggregate", ((Map<?, ?>) aggregateResponse.getAnalysisData()).get("type"));
        assertEquals(1L, pairResponse.getTotalElements());
        assertEquals("组合说明", pairResponse.getExplanation());
        assertEquals(List.of("杭州"), pairResponse.getParsedIntent().getKeywords());
        assertEquals("pair", ((Map<?, ?>) pairResponse.getAnalysisData()).get("type"));
        assertEquals(1L, growthResponse.getTotalElements());
        assertEquals("变化说明", growthResponse.getExplanation());
        assertEquals("growth", ((Map<?, ?>) growthResponse.getAnalysisData()).get("type"));
    }

    private List<AiSearchPersonAggregate> extractPersonAggregates(AiSearchExecutionResult executionResult, String outputKey) {
        @SuppressWarnings("unchecked")
        List<AiSearchPersonAggregate> values = (List<AiSearchPersonAggregate>) executionResult.getFinalOutputs().get(outputKey);
        return values == null ? List.of() : values;
    }

    private List<AiSearchPersonPairAggregate> extractPairAggregates(AiSearchExecutionResult executionResult, String outputKey) {
        @SuppressWarnings("unchecked")
        List<AiSearchPersonPairAggregate> values = (List<AiSearchPersonPairAggregate>) executionResult.getFinalOutputs().get(outputKey);
        return values == null ? List.of() : values;
    }

    private List<AiSearchPersonGrowthAggregate> extractGrowthAggregates(AiSearchExecutionResult executionResult, String outputKey) {
        @SuppressWarnings("unchecked")
        List<AiSearchPersonGrowthAggregate> values = (List<AiSearchPersonGrowthAggregate>) executionResult.getFinalOutputs().get(outputKey);
        return values == null ? List.of() : values;
    }

    private AiSearchPersonAggregate personAggregate(Long id, String name, Integer count) {
        AiSearchPersonAggregate aggregate = new AiSearchPersonAggregate();
        aggregate.setPersonId(id);
        aggregate.setPersonName(name);
        aggregate.setMatchedPhotoCount(count);
        return aggregate;
    }

    private PersonSummaryDTO personSummary(Long id, String name) {
        PersonSummaryDTO summary = new PersonSummaryDTO();
        summary.setId(id);
        summary.setName(name);
        return summary;
    }

    private AiSearchPersonPairAggregate pairAggregate(Long aId, String aName, Long bId, String bName, Integer count) {
        AiSearchPersonPairAggregate aggregate = new AiSearchPersonPairAggregate();
        aggregate.setPersonAId(aId);
        aggregate.setPersonAName(aName);
        aggregate.setPersonBId(bId);
        aggregate.setPersonBName(bName);
        aggregate.setMatchedPhotoCount(count);
        return aggregate;
    }

    private AiSearchPersonGrowthAggregate growthAggregate(Long id, String name) {
        AiSearchPersonGrowthAggregate aggregate = new AiSearchPersonGrowthAggregate();
        aggregate.setPersonId(id);
        aggregate.setPersonName(name);
        return aggregate;
    }
}
