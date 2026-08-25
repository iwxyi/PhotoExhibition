package com.photoexhibition.aisearch.validation;

import com.photoexhibition.dto.AiSearchIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

@Component
@RequiredArgsConstructor
public class AiSearchAnalysisFallbackDescriptorBuilder {

    private final AiSearchAnalysisFallbackIntentFactory aiSearchAnalysisFallbackIntentFactory;
    private final AiSearchAnalysisFallbackSpecBuilder aiSearchAnalysisFallbackSpecBuilder;

    public Result build(String routingType,
                        String resolvedQuery,
                        AiSearchAnalysisFallbackRequest routingRequest,
                        Predicate<String> explicitAnchorDetector,
                        Function<String, YearRange> yearResolver,
                        Function<String, String> comparisonNoiseStripper,
                        Function<AiSearchIntent, String> keywordSummaryBuilder,
                        Function<String, AiSearchIntent> locationIntentBuilder,
                        Function<String, AiSearchIntent> albumIntentBuilder,
                        Function<String, AiSearchIntent> monthIntentBuilder,
                        Function<String, AiSearchIntent> countIntentBuilder,
                        Function<String, AiSearchIntent> personIntentBuilder,
                        Function<String, AiSearchIntent> personCooccurrenceIntentBuilder,
                        Function<String, AiSearchIntent> personPairIntentBuilder,
                        Function<String, AiSearchIntent> dayIntentBuilder,
                        Function<String, AiSearchIntent> tagIntentBuilder,
                        Function<String, AiSearchIntent> themeIntentBuilder,
                        Function<String, AiSearchIntent> yearCompareIntentBuilder) {
        if (routingType == null || routingType.isBlank() || resolvedQuery == null || resolvedQuery.isBlank()) {
            return null;
        }

        Result descriptor = new Result();
        descriptor.normalizeQuery = resolvedQuery;
        descriptor.applyRelativeYearRange = !"year_compare".equals(routingType);

        AiSearchAnalysisFallbackRequest request = cloneRequest(routingRequest);
        request.setRoutingType(routingType);
        request.setResolvedQuery(resolvedQuery);
        request.setExplicitAnchorPerson(
            request.isExplicitAnchorPerson() || (explicitAnchorDetector != null && explicitAnchorDetector.test(resolvedQuery))
        );

        if ("year_compare".equals(routingType)
            && (request.getLeftYear() == null || request.getRightYear() == null)
            && yearResolver != null) {
            YearRange comparison = yearResolver.apply(resolvedQuery);
            if (comparison != null) {
                request.setLeftYear(request.getLeftYear() != null ? request.getLeftYear() : comparison.leftYear);
                request.setRightYear(request.getRightYear() != null ? request.getRightYear() : comparison.rightYear);
            }
            if (comparisonNoiseStripper != null) {
                descriptor.normalizeQuery = comparisonNoiseStripper.apply(resolvedQuery);
            }
            descriptor.applyRelativeYearRange = false;
        }

        AiSearchIntent baseIntent = aiSearchAnalysisFallbackIntentFactory.build(
            routingType,
            request,
            locationIntentBuilder,
            albumIntentBuilder,
            monthIntentBuilder,
            countIntentBuilder,
            personIntentBuilder,
            personCooccurrenceIntentBuilder,
            personPairIntentBuilder,
            dayIntentBuilder,
            tagIntentBuilder,
            themeIntentBuilder,
            yearCompareIntentBuilder
        );
        if (baseIntent == null) {
            return null;
        }
        if ((request.getKeywordSummary() == null || request.getKeywordSummary().isBlank())
            && keywordSummaryBuilder != null) {
            request.setKeywordSummary(keywordSummaryBuilder.apply(baseIntent));
        }

        descriptor.intent = aiSearchAnalysisFallbackSpecBuilder.build(request, baseIntent);
        return descriptor.intent == null ? null : descriptor;
    }

    private AiSearchAnalysisFallbackRequest cloneRequest(AiSearchAnalysisFallbackRequest request) {
        if (request == null) {
            return new AiSearchAnalysisFallbackRequest();
        }
        AiSearchAnalysisFallbackRequest clone = new AiSearchAnalysisFallbackRequest();
        clone.setRoutingType(request.getRoutingType());
        clone.setResolvedQuery(request.getResolvedQuery());
        clone.setExplicitAnchorPerson(request.isExplicitAnchorPerson());
        clone.setLeftYear(request.getLeftYear());
        clone.setRightYear(request.getRightYear());
        clone.setKeywordSummary(request.getKeywordSummary());
        clone.setAiDerived(request.isAiDerived());
        if (request.getTopicKeywords() != null) {
            clone.setTopicKeywords(new ArrayList<>(request.getTopicKeywords()));
        }
        return clone;
    }

    public static class YearRange {
        public final Integer leftYear;
        public final Integer rightYear;

        public YearRange(Integer leftYear, Integer rightYear) {
            this.leftYear = leftYear;
            this.rightYear = rightYear;
        }
    }

    public static class Result {
        public AiSearchIntent intent;
        public String normalizeQuery;
        public boolean applyRelativeYearRange;
    }
}
