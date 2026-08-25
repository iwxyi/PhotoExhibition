package com.photoexhibition.service;

import com.photoexhibition.aisearch.executor.AiSearchExecutionResult;
import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.dto.AiSearchIntent;
import com.photoexhibition.dto.AiSearchResponse;
import com.photoexhibition.dto.AlbumDTO;
import com.photoexhibition.dto.PersonSummaryDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class AiSearchStructuredPhotoResponseSupport {

    public AiSearchIntent ensureStructuredMatchedPhotoIntentDefaults(AiSearchIntent sourceIntent,
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

    public AiSearchResponse buildStructuredMatchedPhotoAnalysisResponse(AiSearchPlan plan,
                                                                        AiSearchIntent intent,
                                                                        AiSearchService.PhotoSearchExecution photoSearch,
                                                                        List<AlbumDTO> albums,
                                                                        AiSearchExecutionResult executionResult,
                                                                        AiSearchAnalysisFlowSupport.PlannedResponseBuilder plannedResponseBuilder,
                                                                        PersonSummariesExtractor personSummariesExtractor,
                                                                        PersonAggregateCountExtractor personAggregateCountExtractor,
                                                                        PersonPairCountExtractor personPairCountExtractor,
                                                                        AnalysisDataBuilder overviewAnalysisDataBuilder,
                                                                        AnalysisDataBuilder cooccurrenceAnalysisDataBuilder,
                                                                        AnalysisDataBuilder pairAnalysisDataBuilder,
                                                                        StructuredYearCompareResponseBuilder structuredYearCompareResponseBuilder,
                                                                        int page,
                                                                        int size) {
        switch (plan.getPlanType()) {
            case "count_overview":
            case "album_overview":
            case "month_overview":
            case "location_overview":
            case "day_overview":
            case "tag_overview":
            case "theme_overview":
                return plannedResponseBuilder.build(
                    plan.getQueryMode(),
                    intent,
                    photoSearch,
                    albums,
                    Collections.emptyList(),
                    photoSearch.totalMatched,
                    plan,
                    executionResult
                );
            case "person_overview": {
                List<PersonSummaryDTO> persons = personSummariesExtractor.extract(executionResult, "limited_persons");
                long totalPersons = personAggregateCountExtractor.count(executionResult, "sorted_persons");
                AiSearchResponse response = plannedResponseBuilder.build(
                    plan.getQueryMode(),
                    intent,
                    photoSearch,
                    albums,
                    persons,
                    totalPersons,
                    plan,
                    executionResult
                );
                response.setAnalysisData(overviewAnalysisDataBuilder.build(executionResult, response.getAnswer()));
                return response;
            }
            case "person_cooccurrence": {
                List<PersonSummaryDTO> persons = personSummariesExtractor.extract(executionResult, "limited_cooccurring_persons");
                long totalPersons = personAggregateCountExtractor.count(executionResult, "sorted_cooccurring_persons");
                AiSearchResponse response = plannedResponseBuilder.build(
                    plan.getQueryMode(),
                    intent,
                    photoSearch,
                    albums,
                    persons,
                    totalPersons,
                    plan,
                    executionResult
                );
                response.setAnalysisData(cooccurrenceAnalysisDataBuilder.build(executionResult, response.getAnswer()));
                return response;
            }
            case "person_pair_cooccurrence": {
                long totalPairs = personPairCountExtractor.count(executionResult, "sorted_cooccurring_pairs");
                AiSearchResponse response = plannedResponseBuilder.build(
                    plan.getQueryMode(),
                    intent,
                    photoSearch,
                    albums,
                    Collections.emptyList(),
                    totalPairs,
                    plan,
                    executionResult
                );
                response.setAnalysisData(pairAnalysisDataBuilder.build(executionResult, response.getAnswer()));
                return response;
            }
            case "year_compare":
                return structuredYearCompareResponseBuilder.build(intent, plan, page, size);
            default:
                return null;
        }
    }

    @FunctionalInterface
    public interface PersonSummariesExtractor {
        List<PersonSummaryDTO> extract(AiSearchExecutionResult executionResult, String outputKey);
    }

    @FunctionalInterface
    public interface PersonAggregateCountExtractor {
        long count(AiSearchExecutionResult executionResult, String outputKey);
    }

    @FunctionalInterface
    public interface PersonPairCountExtractor {
        long count(AiSearchExecutionResult executionResult, String outputKey);
    }

    @FunctionalInterface
    public interface AnalysisDataBuilder {
        Map<String, Object> build(AiSearchExecutionResult executionResult, String answer);
    }

    @FunctionalInterface
    public interface StructuredYearCompareResponseBuilder {
        AiSearchResponse build(AiSearchIntent intent, AiSearchPlan plan, int page, int size);
    }
}
