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
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.Supplier;

@Component
public class AiSearchPersonResponseSupport {

    private final AiSearchPersonAnalysisSupport aiSearchPersonAnalysisSupport;
    private final AiSearchAnalysisFlowSupport aiSearchAnalysisFlowSupport;
    private final AiSearchExecutionResultSupport aiSearchExecutionResultSupport;

    public AiSearchPersonResponseSupport(AiSearchPersonAnalysisSupport aiSearchPersonAnalysisSupport,
                                         AiSearchAnalysisFlowSupport aiSearchAnalysisFlowSupport,
                                         AiSearchExecutionResultSupport aiSearchExecutionResultSupport) {
        this.aiSearchPersonAnalysisSupport = aiSearchPersonAnalysisSupport;
        this.aiSearchAnalysisFlowSupport = aiSearchAnalysisFlowSupport;
        this.aiSearchExecutionResultSupport = aiSearchExecutionResultSupport;
    }

    public AiSearchResponse buildPersonOverviewResponse(String query,
                                                        int page,
                                                        int size,
                                                        String queryMode,
                                                        Supplier<AiSearchIntent> intentSupplier,
                                                        Supplier<AiSearchPlan> planSupplier,
                                                        AiSearchAnalysisFlowSupport.IntentNormalizer intentNormalizer,
                                                        AiSearchAnalysisFlowSupport.PhotoSearchExecutor photoSearchExecutor,
                                                        AiSearchAnalysisFlowSupport.MatchedPhotoPlanExecutor matchedPhotoPlanExecutor,
                                                        AiSearchAnalysisFlowSupport.PlannedResponseBuilder plannedResponseBuilder,
                                                        Function<AiSearchIntent, String> periodLabelBuilder,
                                                        AlbumsFetcher albumsFetcher,
                                                        PersonSummariesExtractor personSummariesExtractor,
                                                        PersonAggregatesExtractor personAggregatesExtractor) {
        AiSearchIntent intent = intentSupplier.get();
        intentNormalizer.normalize(query, intent, true);

        AiSearchService.PhotoSearchExecution photoSearch = photoSearchExecutor.execute(intent, page, size);
        AiSearchPlan plan = planSupplier.get();
        AiSearchExecutionResult executionResult = matchedPhotoPlanExecutor.execute(
            query,
            plan,
            photoSearch,
            "person_overview_metrics",
            result -> buildOverviewMetrics(intent, photoSearch, result, periodLabelBuilder, personAggregatesExtractor)
        );
        List<AlbumDTO> albums = albumsFetcher.fetch(photoSearch);
        List<PersonSummaryDTO> persons = personSummariesExtractor.extract(executionResult, "limited_persons");
        long totalPersons = personAggregatesExtractor.extract(executionResult, "sorted_persons").size();

        AiSearchResponse response = plannedResponseBuilder.build(
            queryMode,
            intent,
            photoSearch,
            albums,
            persons,
            totalPersons,
            plan,
            executionResult
        );
        response.setAnalysisData(buildOverviewAnalysisData(executionResult, response.getAnswer()));
        return response;
    }

    public AiSearchResponse buildPersonCooccurrenceResponse(String query,
                                                            int page,
                                                            int size,
                                                            String queryMode,
                                                            Supplier<AiSearchIntent> intentSupplier,
                                                            AiSearchAnalysisFlowSupport.IntentNormalizer intentNormalizer,
                                                            AiSearchAnalysisFlowSupport.PhotoSearchExecutor photoSearchExecutor,
                                                            PersonCooccurrencePlanSupplier planSupplier,
                                                            AiSearchAnalysisFlowSupport.MatchedPhotoPlanExecutor matchedPhotoPlanExecutor,
                                                            AiSearchAnalysisFlowSupport.PlannedResponseBuilder plannedResponseBuilder,
                                                            Function<AiSearchIntent, String> periodLabelBuilder,
                                                            AlbumsFetcher albumsFetcher,
                                                            PersonSummariesExtractor personSummariesExtractor,
                                                            PersonAggregatesExtractor personAggregatesExtractor,
                                                            LongFunction<String> personNameResolver,
                                                            EmptyAnalysisResponseBuilder emptyAnalysisResponseBuilder) {
        AiSearchIntent intent = intentSupplier.get();
        intentNormalizer.normalize(query, intent, true);

        Long anchorPersonId = firstPersonId(intent);
        if (anchorPersonId == null) {
            return emptyAnalysisResponseBuilder.build(
                queryMode,
                intent,
                "未能识别人物共现分析中的锚点人物",
                "检索结论：未识别出要分析的人物，暂时无法判断共同出现关系。"
            );
        }

        AiSearchService.PhotoSearchExecution photoSearch = photoSearchExecutor.execute(intent, page, size);
        AiSearchPlan plan = planSupplier.plan(query, anchorPersonId, page * size, size);
        plan.getMetadata().put("anchorPersonName", personNameResolver.apply(anchorPersonId));
        AiSearchExecutionResult executionResult = matchedPhotoPlanExecutor.execute(
            query,
            plan,
            photoSearch,
            "person_cooccurrence_metrics",
            result -> buildCooccurrenceMetrics(
                intent,
                photoSearch,
                result,
                anchorPersonId,
                periodLabelBuilder,
                personAggregatesExtractor,
                personNameResolver
            )
        );
        return aiSearchAnalysisFlowSupport.buildPersonAggregateAnalysisResponse(
            queryMode,
            intent,
            photoSearch,
            plan,
            executionResult,
            "limited_cooccurring_persons",
            "sorted_cooccurring_persons",
            albumsFetcher::fetch,
            personSummariesExtractor::extract,
            (result, outputKey) -> personAggregatesExtractor.extract(result, outputKey).size(),
            plannedResponseBuilder,
            response -> response.setAnalysisData(buildCooccurrenceAnalysisData(executionResult, response.getAnswer()))
        );
    }

    public AiSearchResponse buildPersonPairCooccurrenceResponse(String query,
                                                                int page,
                                                                int size,
                                                                String queryMode,
                                                                Supplier<AiSearchIntent> intentSupplier,
                                                                AiSearchAnalysisFlowSupport.IntentNormalizer intentNormalizer,
                                                                AiSearchAnalysisFlowSupport.PhotoSearchExecutor photoSearchExecutor,
                                                                AiSearchAnalysisFlowSupport.MatchedPhotoPlanExecutor matchedPhotoPlanExecutor,
                                                                AiSearchAnalysisFlowSupport.PlannedResponseBuilder plannedResponseBuilder,
                                                                Function<AiSearchIntent, String> periodLabelBuilder,
                                                                AlbumsFetcher albumsFetcher,
                                                                Supplier<AiSearchPlan> planSupplier,
                                                                PersonPairAggregatesExtractor pairAggregatesExtractor) {
        AiSearchIntent intent = intentSupplier.get();
        intentNormalizer.normalize(query, intent, true);

        AiSearchService.PhotoSearchExecution photoSearch = photoSearchExecutor.execute(intent, page, size);
        AiSearchPlan plan = planSupplier.get();
        AiSearchExecutionResult executionResult = matchedPhotoPlanExecutor.execute(
            query,
            plan,
            photoSearch,
            "person_pair_cooccurrence_metrics",
            result -> buildPairCooccurrenceMetrics(intent, photoSearch, result, periodLabelBuilder, pairAggregatesExtractor)
        );
        return aiSearchAnalysisFlowSupport.buildPersonPairAnalysisResponse(
            queryMode,
            intent,
            photoSearch,
            plan,
            executionResult,
            "sorted_cooccurring_pairs",
            albumsFetcher::fetch,
            (result, outputKey) -> pairAggregatesExtractor.extract(result, outputKey).size(),
            plannedResponseBuilder,
            response -> response.setAnalysisData(buildPairCooccurrenceAnalysisData(executionResult, response.getAnswer()))
        );
    }

    public Map<String, Object> buildOverviewMetrics(AiSearchIntent intent,
                                                    AiSearchService.PhotoSearchExecution photoSearch,
                                                    AiSearchExecutionResult executionResult,
                                                    Function<AiSearchIntent, String> periodLabelBuilder,
                                                    PersonAggregatesExtractor personAggregatesExtractor) {
        return aiSearchPersonAnalysisSupport.buildOverviewMetrics(
            periodLabelBuilder.apply(intent),
            personAggregatesExtractor.extract(executionResult, "sorted_persons")
        );
    }

    public Map<String, Object> buildCooccurrenceMetrics(AiSearchIntent intent,
                                                        AiSearchService.PhotoSearchExecution photoSearch,
                                                        AiSearchExecutionResult executionResult,
                                                        Long anchorPersonId,
                                                        Function<AiSearchIntent, String> periodLabelBuilder,
                                                        PersonAggregatesExtractor personAggregatesExtractor,
                                                        LongFunction<String> personNameResolver) {
        return aiSearchPersonAnalysisSupport.buildCooccurrenceMetrics(
            periodLabelBuilder.apply(intent),
            anchorPersonId == null ? null : personNameResolver.apply(anchorPersonId),
            photoSearch.totalMatched,
            personAggregatesExtractor.extract(executionResult, "sorted_cooccurring_persons")
        );
    }

    public Map<String, Object> buildPairCooccurrenceMetrics(AiSearchIntent intent,
                                                            AiSearchService.PhotoSearchExecution photoSearch,
                                                            AiSearchExecutionResult executionResult,
                                                            Function<AiSearchIntent, String> periodLabelBuilder,
                                                            PersonPairAggregatesExtractor pairAggregatesExtractor) {
        return aiSearchPersonAnalysisSupport.buildPairCooccurrenceMetrics(
            periodLabelBuilder.apply(intent),
            photoSearch.totalMatched,
            pairAggregatesExtractor.extract(executionResult, "sorted_cooccurring_pairs")
        );
    }

    public Map<String, Object> buildOverviewAnalysisData(AiSearchExecutionResult executionResult, String answer) {
        return aiSearchPersonAnalysisSupport.buildOverviewAnalysisData(
            aiSearchExecutionResultSupport.extractMetrics(executionResult, "person_overview_metrics"),
            answer
        );
    }

    public Map<String, Object> buildCooccurrenceAnalysisData(AiSearchExecutionResult executionResult, String answer) {
        return aiSearchPersonAnalysisSupport.buildCooccurrenceAnalysisData(
            aiSearchExecutionResultSupport.extractMetrics(executionResult, "person_cooccurrence_metrics"),
            answer
        );
    }

    public Map<String, Object> buildPairCooccurrenceAnalysisData(AiSearchExecutionResult executionResult, String answer) {
        return aiSearchPersonAnalysisSupport.buildPairCooccurrenceAnalysisData(
            aiSearchExecutionResultSupport.extractMetrics(executionResult, "person_pair_cooccurrence_metrics"),
            answer
        );
    }

    public AiSearchResponse buildStructuredPersonAggregateResponse(String originalQuery,
                                                                   AiSearchIntent sourceIntent,
                                                                   AiSearchPlan plan,
                                                                   String allOutputKey,
                                                                   String pagedOutputKey,
                                                                   String defaultResultType,
                                                                   String defaultExplanation,
                                                                   StructuredPlanExecutor structuredPlanExecutor,
                                                                   PersonAggregatesExtractor personAggregatesExtractor,
                                                                   Function<AiSearchPersonAggregate, PersonSummaryDTO> personSummaryMapper,
                                                                   EvidenceReducer evidenceReducer,
                                                                   AnswerResolver answerResolver,
                                                                   ExecutionPlanSummaryBuilder executionPlanSummaryBuilder) {
        return buildStructuredPersonAggregateResponse(
            originalQuery,
            sourceIntent,
            plan,
            allOutputKey,
            pagedOutputKey,
            defaultResultType,
            defaultExplanation,
            structuredPlanExecutor,
            personAggregatesExtractor,
            personSummaryMapper,
            evidenceReducer,
            answerResolver,
            executionPlanSummaryBuilder,
            null,
            null
        );
    }

    public AiSearchResponse buildStructuredPersonAggregateResponse(String originalQuery,
                                                                   AiSearchIntent sourceIntent,
                                                                   AiSearchPlan plan,
                                                                   String allOutputKey,
                                                                   String pagedOutputKey,
                                                                   String defaultResultType,
                                                                   String defaultExplanation,
                                                                   StructuredPlanExecutor structuredPlanExecutor,
                                                                   PersonAggregatesExtractor personAggregatesExtractor,
                                                                   Function<AiSearchPersonAggregate, PersonSummaryDTO> personSummaryMapper,
                                                                   EvidenceReducer evidenceReducer,
                                                                   AnswerResolver answerResolver,
                                                                   ExecutionPlanSummaryBuilder executionPlanSummaryBuilder,
                                                                   StructuredIntentCustomizer intentCustomizer,
                                                                   StructuredResponseCustomizer responseCustomizer) {
        AiSearchExecutionResult executionResult = structuredPlanExecutor.execute(originalQuery, plan);
        List<AiSearchPersonAggregate> allPersons = personAggregatesExtractor.extract(executionResult, allOutputKey);
        List<AiSearchPersonAggregate> pagedPersons = personAggregatesExtractor.extract(executionResult, pagedOutputKey);
        List<PersonSummaryDTO> personResults = pagedPersons.stream()
            .map(personSummaryMapper)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toList());

        AiSearchIntent intent = ensureStructuredIntentDefaults(sourceIntent, defaultResultType, defaultExplanation);
        if (intentCustomizer != null) {
            intentCustomizer.customize(intent, plan, executionResult);
        }
        AiSearchEvidenceBundle evidenceBundle = evidenceReducer.reduce(plan, executionResult);
        AiSearchResponse response = buildStructuredResponseSkeleton(
            plan,
            sourceIntent != null,
            intent,
            answerResolver.resolve(evidenceBundle),
            executionPlanSummaryBuilder.build(plan, executionResult, evidenceBundle, true)
        );
        response.setPersons(personResults);
        response.setTotalElements(allPersons.size());
        if (responseCustomizer != null) {
            responseCustomizer.customize(response, executionResult);
        }
        return response;
    }

    public AiSearchResponse buildStructuredPersonGrowthResponse(String originalQuery,
                                                                AiSearchIntent sourceIntent,
                                                                AiSearchPlan plan,
                                                                String allOutputKey,
                                                                String pagedOutputKey,
                                                                String defaultResultType,
                                                                String defaultExplanation,
                                                                StructuredPlanExecutor structuredPlanExecutor,
                                                                PersonGrowthAggregatesExtractor personGrowthAggregatesExtractor,
                                                                Function<AiSearchPersonGrowthAggregate, PersonSummaryDTO> personSummaryMapper,
                                                                EvidenceReducer evidenceReducer,
                                                                AnswerResolver answerResolver,
                                                                ExecutionPlanSummaryBuilder executionPlanSummaryBuilder,
                                                                StructuredIntentCustomizer intentCustomizer,
                                                                StructuredResponseCustomizer responseCustomizer) {
        AiSearchExecutionResult executionResult = structuredPlanExecutor.execute(originalQuery, plan);
        List<AiSearchPersonGrowthAggregate> allPersons = personGrowthAggregatesExtractor.extract(executionResult, allOutputKey);
        List<AiSearchPersonGrowthAggregate> pagedPersons = personGrowthAggregatesExtractor.extract(executionResult, pagedOutputKey);
        List<PersonSummaryDTO> personResults = pagedPersons.stream()
            .map(personSummaryMapper)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toList());

        AiSearchIntent intent = ensureStructuredIntentDefaults(sourceIntent, defaultResultType, defaultExplanation);
        if (intentCustomizer != null) {
            intentCustomizer.customize(intent, plan, executionResult);
        }
        AiSearchEvidenceBundle evidenceBundle = evidenceReducer.reduce(plan, executionResult);
        AiSearchResponse response = buildStructuredResponseSkeleton(
            plan,
            sourceIntent != null,
            intent,
            answerResolver.resolve(evidenceBundle),
            executionPlanSummaryBuilder.build(plan, executionResult, evidenceBundle, true)
        );
        response.setPersons(personResults);
        response.setTotalElements(allPersons.size());
        if (responseCustomizer != null) {
            responseCustomizer.customize(response, executionResult);
        }
        return response;
    }

    public AiSearchResponse buildStructuredPersonPairResponse(String originalQuery,
                                                              AiSearchIntent sourceIntent,
                                                              AiSearchPlan plan,
                                                              String allOutputKey,
                                                              String defaultExplanation,
                                                              StructuredPlanExecutor structuredPlanExecutor,
                                                              PersonPairAggregatesExtractor pairAggregatesExtractor,
                                                              EvidenceReducer evidenceReducer,
                                                              AnswerResolver answerResolver,
                                                              ExecutionPlanSummaryBuilder executionPlanSummaryBuilder,
                                                              StructuredResponseCustomizer responseCustomizer) {
        return buildStructuredPersonPairResponse(
            originalQuery,
            sourceIntent,
            plan,
            allOutputKey,
            "albums",
            defaultExplanation,
            structuredPlanExecutor,
            pairAggregatesExtractor,
            evidenceReducer,
            answerResolver,
            executionPlanSummaryBuilder,
            null,
            responseCustomizer
        );
    }

    public AiSearchResponse buildStructuredPersonPairResponse(String originalQuery,
                                                              AiSearchIntent sourceIntent,
                                                              AiSearchPlan plan,
                                                              String allOutputKey,
                                                              String defaultResultType,
                                                              String defaultExplanation,
                                                              StructuredPlanExecutor structuredPlanExecutor,
                                                              PersonPairAggregatesExtractor pairAggregatesExtractor,
                                                              EvidenceReducer evidenceReducer,
                                                              AnswerResolver answerResolver,
                                                              ExecutionPlanSummaryBuilder executionPlanSummaryBuilder,
                                                              StructuredIntentCustomizer intentCustomizer,
                                                              StructuredResponseCustomizer responseCustomizer) {
        AiSearchExecutionResult executionResult = structuredPlanExecutor.execute(originalQuery, plan);
        List<AiSearchPersonPairAggregate> allPairs = pairAggregatesExtractor.extract(executionResult, allOutputKey);
        AiSearchIntent intent = ensureStructuredIntentDefaults(sourceIntent, defaultResultType, defaultExplanation);
        if (intentCustomizer != null) {
            intentCustomizer.customize(intent, plan, executionResult);
        }
        AiSearchEvidenceBundle evidenceBundle = evidenceReducer.reduce(plan, executionResult);
        AiSearchResponse response = buildStructuredResponseSkeleton(
            plan,
            sourceIntent != null,
            intent,
            answerResolver.resolve(evidenceBundle),
            executionPlanSummaryBuilder.build(plan, executionResult, evidenceBundle, true)
        );
        response.setTotalElements(allPairs.size());
        if (responseCustomizer != null) {
            responseCustomizer.customize(response, executionResult);
        }
        return response;
    }

    private Long firstPersonId(AiSearchIntent intent) {
        return intent == null || intent.getPersonIds() == null || intent.getPersonIds().isEmpty()
            ? null
            : intent.getPersonIds().get(0);
    }

    private AiSearchIntent ensureStructuredIntentDefaults(AiSearchIntent sourceIntent,
                                                          String defaultResultType,
                                                          String defaultExplanation) {
        AiSearchIntent intent = sourceIntent != null ? sourceIntent : new AiSearchIntent();
        if (intent.getResultTypes() == null || intent.getResultTypes().isEmpty()) {
            intent.setResultTypes(List.of(defaultResultType));
        }
        intent.setNeedAnswer(true);
        if (intent.getExplanation() == null || intent.getExplanation().isBlank()) {
            intent.setExplanation(defaultExplanation);
        }
        if (intent.getMust() == null) {
            intent.setMust(new ArrayList<>());
        }
        if (intent.getShould() == null) {
            intent.setShould(new ArrayList<>());
        }
        if (intent.getMustNot() == null) {
            intent.setMustNot(new ArrayList<>());
        }
        return intent;
    }

    private AiSearchResponse buildStructuredResponseSkeleton(AiSearchPlan plan,
                                                             boolean usedAi,
                                                             AiSearchIntent intent,
                                                             String answer,
                                                             Map<String, Object> executionPlan) {
        AiSearchResponse response = new AiSearchResponse();
        response.setAiSearchEnabled(true);
        response.setQueryMode(plan.getQueryMode());
        response.setUsedAi(usedAi);
        response.setNeedAnswer(true);
        response.setParsedIntent(intent);
        response.setPhotos(Collections.emptyList());
        response.setAlbums(Collections.emptyList());
        response.setPersons(Collections.emptyList());
        response.setSuggestions(Collections.emptyList());
        response.setSuggestionActions(Collections.emptyList());
        response.setExplanation(intent.getExplanation());
        response.setAnswer(answer);
        response.setExecutionPlan(executionPlan);
        return response;
    }

    @FunctionalInterface
    public interface AlbumsFetcher {
        List<AlbumDTO> fetch(AiSearchService.PhotoSearchExecution photoSearch);
    }

    @FunctionalInterface
    public interface PersonSummariesExtractor {
        List<PersonSummaryDTO> extract(AiSearchExecutionResult executionResult, String outputKey);
    }

    @FunctionalInterface
    public interface PersonAggregatesExtractor {
        List<AiSearchPersonAggregate> extract(AiSearchExecutionResult executionResult, String outputKey);
    }

    @FunctionalInterface
    public interface PersonPairAggregatesExtractor {
        List<AiSearchPersonPairAggregate> extract(AiSearchExecutionResult executionResult, String outputKey);
    }

    @FunctionalInterface
    public interface PersonGrowthAggregatesExtractor {
        List<AiSearchPersonGrowthAggregate> extract(AiSearchExecutionResult executionResult, String outputKey);
    }

    @FunctionalInterface
    public interface PersonCooccurrencePlanSupplier {
        AiSearchPlan plan(String query, Long anchorPersonId, int offset, int size);
    }

    @FunctionalInterface
    public interface EmptyAnalysisResponseBuilder {
        AiSearchResponse build(String queryMode, AiSearchIntent intent, String explanation, String answer);
    }

    @FunctionalInterface
    public interface StructuredPlanExecutor {
        AiSearchExecutionResult execute(String originalQuery, AiSearchPlan plan);
    }

    @FunctionalInterface
    public interface EvidenceReducer {
        AiSearchEvidenceBundle reduce(AiSearchPlan plan, AiSearchExecutionResult executionResult);
    }

    @FunctionalInterface
    public interface AnswerResolver {
        String resolve(AiSearchEvidenceBundle evidenceBundle);
    }

    @FunctionalInterface
    public interface ExecutionPlanSummaryBuilder {
        Map<String, Object> build(AiSearchPlan plan,
                                  AiSearchExecutionResult executionResult,
                                  AiSearchEvidenceBundle evidenceBundle,
                                  boolean includeEvidence);
    }

    @FunctionalInterface
    public interface StructuredResponseCustomizer {
        void customize(AiSearchResponse response, AiSearchExecutionResult executionResult);
    }

    @FunctionalInterface
    public interface StructuredIntentCustomizer {
        void customize(AiSearchIntent intent, AiSearchPlan plan, AiSearchExecutionResult executionResult);
    }
}
