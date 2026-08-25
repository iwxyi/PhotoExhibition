package com.photoexhibition.service;

import com.photoexhibition.aisearch.executor.AiSearchExecutionResult;
import com.photoexhibition.aisearch.orchestration.AiSearchOverviewAnalysisSupport;
import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.dto.AiSearchIntent;
import com.photoexhibition.dto.AiSearchResponse;
import com.photoexhibition.dto.AlbumDTO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
public class AiSearchOverviewResponseSupport {

    private final AiSearchOverviewAnalysisSupport aiSearchOverviewAnalysisSupport;
    private final AiSearchAnalysisFlowSupport aiSearchAnalysisFlowSupport;

    public AiSearchOverviewResponseSupport(AiSearchOverviewAnalysisSupport aiSearchOverviewAnalysisSupport,
                                           AiSearchAnalysisFlowSupport aiSearchAnalysisFlowSupport) {
        this.aiSearchOverviewAnalysisSupport = aiSearchOverviewAnalysisSupport;
        this.aiSearchAnalysisFlowSupport = aiSearchAnalysisFlowSupport;
    }

    public AiSearchResponse buildSummaryOverviewResponse(String query,
                                                         int page,
                                                         int size,
                                                         String queryMode,
                                                         Supplier<AiSearchIntent> intentSupplier,
                                                         Supplier<AiSearchPlan> planSupplier,
                                                         String metricsKey,
                                                         Function<AiSearchExecutionResult, Map<String, Long>> countExtractor,
                                                         int summaryLimit,
                                                         AiSearchAnalysisFlowSupport.OverviewAlbumsBuilder albumsBuilder,
                                                         AiSearchAnalysisFlowSupport.IntentNormalizer intentNormalizer,
                                                         AiSearchAnalysisFlowSupport.PhotoSearchExecutor photoSearchExecutor,
                                                         AiSearchAnalysisFlowSupport.MatchedPhotoPlanExecutor matchedPhotoPlanExecutor,
                                                         AiSearchAnalysisFlowSupport.PlannedResponseBuilder plannedResponseBuilder,
                                                         Function<AiSearchIntent, String> periodLabelBuilder) {
        return aiSearchAnalysisFlowSupport.buildMatchedPhotoOverviewAnalysisResponse(
            query,
            page,
            size,
            queryMode,
            intentSupplier,
            planSupplier,
            metricsKey,
            (intent, photoSearch, result) -> buildSummaryMetrics(
                intent,
                photoSearch,
                countExtractor.apply(result),
                summaryLimit,
                periodLabelBuilder
            ),
            albumsBuilder,
            intentNormalizer,
            photoSearchExecutor,
            matchedPhotoPlanExecutor,
            plannedResponseBuilder
        );
    }

    public AiSearchResponse buildAlbumOverviewResponse(String query,
                                                       int page,
                                                       int size,
                                                       String queryMode,
                                                       Supplier<AiSearchIntent> intentSupplier,
                                                       Supplier<AiSearchPlan> planSupplier,
                                                       String metricsKey,
                                                       Function<AiSearchExecutionResult, Map<Long, Long>> albumCountExtractor,
                                                       AlbumsByCountFetcher albumsByCountFetcher,
                                                       int responseAlbumLimit,
                                                       int summaryLimit,
                                                       AiSearchAnalysisFlowSupport.IntentNormalizer intentNormalizer,
                                                       AiSearchAnalysisFlowSupport.PhotoSearchExecutor photoSearchExecutor,
                                                       AiSearchAnalysisFlowSupport.MatchedPhotoPlanExecutor matchedPhotoPlanExecutor,
                                                       AiSearchAnalysisFlowSupport.PlannedResponseBuilder plannedResponseBuilder,
                                                       Function<AiSearchIntent, String> periodLabelBuilder,
                                                       Function<Long, String> fallbackAlbumNameBuilder) {
        return aiSearchAnalysisFlowSupport.buildMatchedPhotoOverviewAnalysisResponse(
            query,
            page,
            size,
            queryMode,
            intentSupplier,
            planSupplier,
            metricsKey,
            (intent, photoSearch, result) -> {
                Map<Long, Long> albumCounts = albumCountExtractor.apply(result);
                return buildAlbumMetrics(
                    intent,
                    photoSearch,
                    albumCounts,
                    albumsByCountFetcher,
                    summaryLimit,
                    periodLabelBuilder,
                    fallbackAlbumNameBuilder
                );
            },
            (photoSearch, result) -> albumsByCountFetcher.fetch(albumCountExtractor.apply(result), responseAlbumLimit),
            intentNormalizer,
            photoSearchExecutor,
            matchedPhotoPlanExecutor,
            plannedResponseBuilder
        );
    }

    public AiSearchResponse buildCountOverviewResponse(String query,
                                                       int page,
                                                       int size,
                                                       String queryMode,
                                                       Supplier<AiSearchIntent> intentSupplier,
                                                       Supplier<AiSearchPlan> planSupplier,
                                                       String metricsKey,
                                                       Function<AiSearchExecutionResult, Map<Long, Long>> albumCountExtractor,
                                                       AlbumsByCountFetcher albumsByCountFetcher,
                                                       int responseAlbumLimit,
                                                       AiSearchAnalysisFlowSupport.IntentNormalizer intentNormalizer,
                                                       AiSearchAnalysisFlowSupport.PhotoSearchExecutor photoSearchExecutor,
                                                       AiSearchAnalysisFlowSupport.MatchedPhotoPlanExecutor matchedPhotoPlanExecutor,
                                                       AiSearchAnalysisFlowSupport.PlannedResponseBuilder plannedResponseBuilder,
                                                       Function<AiSearchIntent, String> periodLabelBuilder) {
        return aiSearchAnalysisFlowSupport.buildMatchedPhotoOverviewAnalysisResponse(
            query,
            page,
            size,
            queryMode,
            intentSupplier,
            planSupplier,
            metricsKey,
            (intent, photoSearch, result) -> buildCountMetrics(
                intent,
                photoSearch,
                albumCountExtractor.apply(result),
                periodLabelBuilder
            ),
            (photoSearch, result) -> albumsByCountFetcher.fetch(albumCountExtractor.apply(result), responseAlbumLimit),
            intentNormalizer,
            photoSearchExecutor,
            matchedPhotoPlanExecutor,
            plannedResponseBuilder
        );
    }

    public Map<String, Object> buildSummaryMetrics(AiSearchIntent intent,
                                                   AiSearchService.PhotoSearchExecution photoSearch,
                                                   Map<String, Long> counts,
                                                   int summaryLimit,
                                                   Function<AiSearchIntent, String> periodLabelBuilder) {
        return aiSearchOverviewAnalysisSupport.buildSummaryMetrics(
            periodLabelBuilder.apply(intent),
            photoSearch.totalMatched,
            buildSummaryItems(counts, summaryLimit)
        );
    }

    public Map<String, Object> buildAlbumMetrics(AiSearchIntent intent,
                                                 AiSearchService.PhotoSearchExecution photoSearch,
                                                 Map<Long, Long> albumCounts,
                                                 AlbumsByCountFetcher albumsByCountFetcher,
                                                 int summaryLimit,
                                                 Function<AiSearchIntent, String> periodLabelBuilder,
                                                 Function<Long, String> fallbackAlbumNameBuilder) {
        List<AlbumDTO> albums = albumsByCountFetcher.fetch(albumCounts, summaryLimit);
        Map<Long, String> albumNames = albums.stream()
            .filter(album -> album.getId() != null)
            .collect(Collectors.toMap(AlbumDTO::getId, AlbumDTO::getName, (left, right) -> left, LinkedHashMap::new));
        return aiSearchOverviewAnalysisSupport.buildAlbumMetrics(
            periodLabelBuilder.apply(intent),
            photoSearch.totalMatched,
            albumCounts.entrySet().stream()
                .limit(summaryLimit)
                .map(entry -> albumNames.getOrDefault(
                    entry.getKey(),
                    fallbackAlbumNameBuilder.apply(entry.getKey())
                ) + "(" + entry.getValue() + "张)")
                .collect(Collectors.toList())
        );
    }

    public Map<String, Object> buildCountMetrics(AiSearchIntent intent,
                                                 AiSearchService.PhotoSearchExecution photoSearch,
                                                 Map<Long, Long> albumCounts,
                                                 Function<AiSearchIntent, String> periodLabelBuilder) {
        return aiSearchOverviewAnalysisSupport.buildCountMetrics(
            periodLabelBuilder.apply(intent),
            photoSearch.totalMatched,
            albumCounts.size()
        );
    }

    private List<String> buildSummaryItems(Map<String, Long> counts, int summaryLimit) {
        return counts.entrySet().stream()
            .limit(summaryLimit)
            .map(entry -> entry.getKey() + "(" + entry.getValue() + "张)")
            .collect(Collectors.toList());
    }

    @FunctionalInterface
    public interface AlbumsByCountFetcher {
        List<AlbumDTO> fetch(Map<Long, Long> albumCounts, int limit);
    }
}
