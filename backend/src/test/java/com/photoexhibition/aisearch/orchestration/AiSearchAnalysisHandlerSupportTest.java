package com.photoexhibition.aisearch.orchestration;

import com.photoexhibition.dto.AiSearchResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiSearchAnalysisHandlerSupportTest {

    private final AiSearchAnalysisHandlerSupport support = new AiSearchAnalysisHandlerSupport();

    @Test
    void shouldBuildHandlersInStableDispatchOrder() {
        Map<String, Supplier<AiSearchResponse>> handlers = support.buildHandlers(new CountingCallbacks());

        assertEquals(List.of(
            "theme",
            "location",
            "album",
            "month",
            "count",
            "person_cooccurrence",
            "person_pair_cooccurrence",
            "person",
            "day",
            "tag",
            "year_compare",
            "body_change"
        ), List.copyOf(handlers.keySet()));
    }

    @Test
    void shouldDelegateEachHandlerToMatchingCallback() {
        CountingCallbacks callbacks = new CountingCallbacks();
        Map<String, Supplier<AiSearchResponse>> handlers = support.buildHandlers(callbacks);

        handlers.forEach((key, supplier) -> assertEquals(key, supplier.get().getAnswer()));
        assertEquals(12, callbacks.invocationCount.get());
    }

    private static final class CountingCallbacks implements AiSearchAnalysisHandlerSupport.HandlerCallbacks {
        private final AtomicInteger invocationCount = new AtomicInteger();

        @Override
        public AiSearchResponse buildThemeOverviewResponse() {
            return response("theme");
        }

        @Override
        public AiSearchResponse buildLocationOverviewResponse() {
            return response("location");
        }

        @Override
        public AiSearchResponse buildAlbumOverviewResponse() {
            return response("album");
        }

        @Override
        public AiSearchResponse buildMonthOverviewResponse() {
            return response("month");
        }

        @Override
        public AiSearchResponse buildCountOverviewResponse() {
            return response("count");
        }

        @Override
        public AiSearchResponse buildPersonCooccurrenceResponse() {
            return response("person_cooccurrence");
        }

        @Override
        public AiSearchResponse buildPersonPairCooccurrenceResponse() {
            return response("person_pair_cooccurrence");
        }

        @Override
        public AiSearchResponse buildPersonOverviewResponse() {
            return response("person");
        }

        @Override
        public AiSearchResponse buildDayOverviewResponse() {
            return response("day");
        }

        @Override
        public AiSearchResponse buildTagOverviewResponse() {
            return response("tag");
        }

        @Override
        public AiSearchResponse buildYearCompareResponse() {
            return response("year_compare");
        }

        @Override
        public AiSearchResponse buildBodyChangeResponse() {
            return response("body_change");
        }

        private AiSearchResponse response(String answer) {
            invocationCount.incrementAndGet();
            AiSearchResponse response = new AiSearchResponse();
            response.setAnswer(answer);
            return response;
        }
    }
}
