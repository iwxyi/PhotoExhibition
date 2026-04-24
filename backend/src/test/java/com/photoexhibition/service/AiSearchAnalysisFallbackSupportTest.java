package com.photoexhibition.service;

import com.photoexhibition.aisearch.validation.AiSearchAnalysisFallbackDescriptorBuilder;
import com.photoexhibition.aisearch.validation.AiSearchAnalysisFallbackIntentFactory;
import com.photoexhibition.aisearch.validation.AiSearchAnalysisFallbackRequest;
import com.photoexhibition.aisearch.validation.AiSearchAnalysisFallbackSpecBuilder;
import com.photoexhibition.dto.AiSearchIntent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AiSearchAnalysisFallbackSupportTest {

    private final AiSearchAnalysisFallbackSupport support = new AiSearchAnalysisFallbackSupport(
        new AiSearchAnalysisFallbackDescriptorBuilder(
            new AiSearchAnalysisFallbackIntentFactory(),
            new AiSearchAnalysisFallbackSpecBuilder()
        )
    );

    @Test
    void shouldResolveDescriptorThroughCallbacks() {
        AiSearchAnalysisFallbackRequest request = new AiSearchAnalysisFallbackRequest();
        request.setResolvedQuery("去年和前年相比樱花拍得更多还是更少");

        AiSearchAnalysisFallbackDescriptorBuilder.Result result = support.resolveDescriptor(
            "year_compare",
            "去年和前年相比樱花拍得更多还是更少",
            request,
            new TestCallbacks()
        );

        assertNotNull(result);
        assertEquals("樱花", result.normalizeQuery);
        assertEquals("count:去年和前年相比樱花拍得更多还是更少", result.intent.getExplanation());
    }

    @Test
    void shouldRejectBlankRoutingInputs() {
        assertNull(support.resolveDescriptor("", "查询", new AiSearchAnalysisFallbackRequest(), new TestCallbacks()));
        assertNull(support.resolveDescriptor("count", "", new AiSearchAnalysisFallbackRequest(), new TestCallbacks()));
        assertNull(support.resolveDescriptor("count", "查询", new AiSearchAnalysisFallbackRequest(), null));
    }

    private static final class TestCallbacks implements AiSearchAnalysisFallbackSupport.AnalysisFallbackCallbacks {
        @Override
        public boolean hasExplicitAnchorPerson(String query) {
            return false;
        }

        @Override
        public AiSearchAnalysisFallbackDescriptorBuilder.YearRange resolveYearRange(String query) {
            return new AiSearchAnalysisFallbackDescriptorBuilder.YearRange(2025, 2024);
        }

        @Override
        public String stripComparisonNoise(String query) {
            return "樱花";
        }

        @Override
        public String buildKeywordSummary(AiSearchIntent intent) {
            return "樱花";
        }

        @Override
        public AiSearchIntent buildLocationIntent(String query) {
            return null;
        }

        @Override
        public AiSearchIntent buildAlbumIntent(String query) {
            return null;
        }

        @Override
        public AiSearchIntent buildMonthIntent(String query) {
            return null;
        }

        @Override
        public AiSearchIntent buildCountIntent(String query) {
            AiSearchIntent intent = new AiSearchIntent();
            intent.setExplanation("count:" + query);
            return intent;
        }

        @Override
        public AiSearchIntent buildPersonIntent(String query) {
            return null;
        }

        @Override
        public AiSearchIntent buildPersonCooccurrenceIntent(String query) {
            return null;
        }

        @Override
        public AiSearchIntent buildPersonPairIntent(String query) {
            return null;
        }

        @Override
        public AiSearchIntent buildDayIntent(String query) {
            return null;
        }

        @Override
        public AiSearchIntent buildTagIntent(String query) {
            return null;
        }

        @Override
        public AiSearchIntent buildThemeIntent(String query) {
            return null;
        }

        @Override
        public AiSearchIntent buildYearCompareIntent(String query) {
            return buildCountIntent(query);
        }
    }
}
