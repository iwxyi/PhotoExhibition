package com.photoexhibition.service;

import com.photoexhibition.aisearch.executor.AiSearchExecutionResult;
import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.dto.AiSearchIntent;
import com.photoexhibition.dto.AiSearchResponse;
import com.photoexhibition.dto.AlbumDTO;
import com.photoexhibition.dto.PersonSummaryDTO;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@Component
public class AiSearchAnalysisFlowSupport {

    public AiSearchResponse buildMatchedPhotoOverviewAnalysisResponse(String query,
                                                                     int page,
                                                                     int size,
                                                                     String queryMode,
                                                                     Supplier<AiSearchIntent> intentSupplier,
                                                                     Supplier<AiSearchPlan> planSupplier,
                                                                     String metricsKey,
                                                                     OverviewMetricsBuilder metricsBuilder,
                                                                     OverviewAlbumsBuilder albumsBuilder,
                                                                     IntentNormalizer intentNormalizer,
                                                                     PhotoSearchExecutor photoSearchExecutor,
                                                                     MatchedPhotoPlanExecutor matchedPhotoPlanExecutor,
                                                                     PlannedResponseBuilder plannedResponseBuilder) {
        AiSearchIntent intent = intentSupplier.get();
        intentNormalizer.normalize(query, intent, true);

        AiSearchService.PhotoSearchExecution photoSearch = photoSearchExecutor.execute(intent, page, size);
        AiSearchPlan plan = planSupplier.get();
        AiSearchExecutionResult executionResult = matchedPhotoPlanExecutor.execute(
            query,
            plan,
            photoSearch,
            metricsKey,
            result -> metricsBuilder.build(intent, photoSearch, result)
        );
        List<AlbumDTO> albums = albumsBuilder.build(photoSearch, executionResult);
        return plannedResponseBuilder.build(
            queryMode,
            intent,
            photoSearch,
            albums,
            Collections.emptyList(),
            photoSearch.totalMatched,
            plan,
            executionResult
        );
    }

    public AiSearchResponse buildPersonAggregateAnalysisResponse(String queryMode,
                                                                AiSearchIntent intent,
                                                                AiSearchService.PhotoSearchExecution photoSearch,
                                                                AiSearchPlan plan,
                                                                AiSearchExecutionResult executionResult,
                                                                String limitedOutputKey,
                                                                String sortedOutputKey,
                                                                AlbumsFetcher albumsFetcher,
                                                                PersonSummariesExtractor personSummariesExtractor,
                                                                AggregateCountExtractor aggregateCountExtractor,
                                                                PlannedResponseBuilder plannedResponseBuilder,
                                                                Consumer<AiSearchResponse> analysisDataAttacher) {
        List<AlbumDTO> albums = albumsFetcher.fetch(photoSearch);
        List<PersonSummaryDTO> persons = personSummariesExtractor.extract(executionResult, limitedOutputKey);
        long totalPersons = aggregateCountExtractor.count(executionResult, sortedOutputKey);
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
        if (analysisDataAttacher != null) {
            analysisDataAttacher.accept(response);
        }
        return response;
    }

    public AiSearchResponse buildPersonPairAnalysisResponse(String queryMode,
                                                            AiSearchIntent intent,
                                                            AiSearchService.PhotoSearchExecution photoSearch,
                                                            AiSearchPlan plan,
                                                            AiSearchExecutionResult executionResult,
                                                            String sortedOutputKey,
                                                            AlbumsFetcher albumsFetcher,
                                                            AggregateCountExtractor aggregateCountExtractor,
                                                            PlannedResponseBuilder plannedResponseBuilder,
                                                            Consumer<AiSearchResponse> analysisDataAttacher) {
        List<AlbumDTO> albums = albumsFetcher.fetch(photoSearch);
        long totalPairs = aggregateCountExtractor.count(executionResult, sortedOutputKey);
        AiSearchResponse response = plannedResponseBuilder.build(
            queryMode,
            intent,
            photoSearch,
            albums,
            Collections.emptyList(),
            totalPairs,
            plan,
            executionResult
        );
        if (analysisDataAttacher != null) {
            analysisDataAttacher.accept(response);
        }
        return response;
    }

    @FunctionalInterface
    public interface OverviewMetricsBuilder {
        Map<String, Object> build(AiSearchIntent intent,
                                  AiSearchService.PhotoSearchExecution photoSearch,
                                  AiSearchExecutionResult executionResult);
    }

    @FunctionalInterface
    public interface OverviewAlbumsBuilder {
        List<AlbumDTO> build(AiSearchService.PhotoSearchExecution photoSearch,
                             AiSearchExecutionResult executionResult);
    }

    @FunctionalInterface
    public interface IntentNormalizer {
        void normalize(String query, AiSearchIntent intent, boolean applyRelativeYearRange);
    }

    @FunctionalInterface
    public interface PhotoSearchExecutor {
        AiSearchService.PhotoSearchExecution execute(AiSearchIntent intent, int page, int size);
    }

    @FunctionalInterface
    public interface MatchedPhotoPlanExecutor {
        AiSearchExecutionResult execute(String query,
                                        AiSearchPlan plan,
                                        AiSearchService.PhotoSearchExecution photoSearch,
                                        String metricsKey,
                                        Function<AiSearchExecutionResult, Map<String, Object>> metricsBuilder);
    }

    @FunctionalInterface
    public interface PlannedResponseBuilder {
        AiSearchResponse build(String queryMode,
                               AiSearchIntent intent,
                               AiSearchService.PhotoSearchExecution photoSearch,
                               List<AlbumDTO> albums,
                               List<PersonSummaryDTO> persons,
                               long totalElements,
                               AiSearchPlan plan,
                               AiSearchExecutionResult executionResult);
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
    public interface AggregateCountExtractor {
        long count(AiSearchExecutionResult executionResult, String outputKey);
    }
}
