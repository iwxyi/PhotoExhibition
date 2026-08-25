package com.photoexhibition.service;

import com.photoexhibition.aisearch.validation.AiSearchAnalysisFallbackDescriptorBuilder;
import com.photoexhibition.aisearch.validation.AiSearchAnalysisFallbackRequest;
import com.photoexhibition.dto.AiSearchIntent;
import org.springframework.stereotype.Component;

@Component
public class AiSearchAnalysisFallbackSupport {

    private final AiSearchAnalysisFallbackDescriptorBuilder aiSearchAnalysisFallbackDescriptorBuilder;

    public AiSearchAnalysisFallbackSupport(AiSearchAnalysisFallbackDescriptorBuilder aiSearchAnalysisFallbackDescriptorBuilder) {
        this.aiSearchAnalysisFallbackDescriptorBuilder = aiSearchAnalysisFallbackDescriptorBuilder;
    }

    public AiSearchAnalysisFallbackDescriptorBuilder.Result resolveDescriptor(String routingType,
                                                                              String resolvedQuery,
                                                                              AiSearchAnalysisFallbackRequest fallbackRequest,
                                                                              AnalysisFallbackCallbacks callbacks) {
        if (routingType == null || routingType.isBlank() || resolvedQuery == null || resolvedQuery.isBlank()) {
            return null;
        }
        if (callbacks == null) {
            return null;
        }
        return aiSearchAnalysisFallbackDescriptorBuilder.build(
            routingType,
            resolvedQuery,
            fallbackRequest,
            callbacks::hasExplicitAnchorPerson,
            callbacks::resolveYearRange,
            callbacks::stripComparisonNoise,
            callbacks::buildKeywordSummary,
            callbacks::buildLocationIntent,
            callbacks::buildAlbumIntent,
            callbacks::buildMonthIntent,
            callbacks::buildCountIntent,
            callbacks::buildPersonIntent,
            callbacks::buildPersonCooccurrenceIntent,
            callbacks::buildPersonPairIntent,
            callbacks::buildDayIntent,
            callbacks::buildTagIntent,
            callbacks::buildThemeIntent,
            callbacks::buildYearCompareIntent
        );
    }

    public interface AnalysisFallbackCallbacks {
        boolean hasExplicitAnchorPerson(String query);
        AiSearchAnalysisFallbackDescriptorBuilder.YearRange resolveYearRange(String query);
        String stripComparisonNoise(String query);
        String buildKeywordSummary(AiSearchIntent intent);
        AiSearchIntent buildLocationIntent(String query);
        AiSearchIntent buildAlbumIntent(String query);
        AiSearchIntent buildMonthIntent(String query);
        AiSearchIntent buildCountIntent(String query);
        AiSearchIntent buildPersonIntent(String query);
        AiSearchIntent buildPersonCooccurrenceIntent(String query);
        AiSearchIntent buildPersonPairIntent(String query);
        AiSearchIntent buildDayIntent(String query);
        AiSearchIntent buildTagIntent(String query);
        AiSearchIntent buildThemeIntent(String query);
        AiSearchIntent buildYearCompareIntent(String query);
    }
}
