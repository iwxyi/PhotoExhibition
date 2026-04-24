package com.photoexhibition.aisearch.orchestration;

import com.photoexhibition.dto.AiSearchResponse;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiSearchAnalysisResponseDispatcherTest {

    private final AiSearchAnalysisResponseDispatcher dispatcher = new AiSearchAnalysisResponseDispatcher();

    @Test
    void shouldDispatchSimpleRouteDirectly() {
        Map<String, Supplier<AiSearchResponse>> handlers = new LinkedHashMap<>();
        handlers.put("theme", () -> response("theme"));

        AiSearchResponse response = dispatcher.dispatch("theme", false, handlers);

        assertEquals("theme", response.getExplanation());
    }

    @Test
    void shouldDispatchCooccurrenceToAnchoredHandlerWhenAnchorExists() {
        Map<String, Supplier<AiSearchResponse>> handlers = new LinkedHashMap<>();
        handlers.put("person_cooccurrence", () -> response("anchored"));
        handlers.put("person_pair_cooccurrence", () -> response("pair"));

        AiSearchResponse response = dispatcher.dispatch("person_pair_cooccurrence", true, handlers);

        assertEquals("anchored", response.getExplanation());
    }

    @Test
    void shouldDispatchCooccurrenceToPairHandlerWhenAnchorMissing() {
        Map<String, Supplier<AiSearchResponse>> handlers = new LinkedHashMap<>();
        handlers.put("person_cooccurrence", () -> response("anchored"));
        handlers.put("person_pair_cooccurrence", () -> response("pair"));

        AiSearchResponse response = dispatcher.dispatch("person_cooccurrence", false, handlers);

        assertEquals("pair", response.getExplanation());
    }

    @Test
    void shouldFailForUnsupportedRoutingType() {
        Map<String, Supplier<AiSearchResponse>> handlers = new LinkedHashMap<>();
        handlers.put("theme", () -> response("theme"));

        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch("unknown", false, handlers));
    }

    private AiSearchResponse response(String explanation) {
        AiSearchResponse response = new AiSearchResponse();
        response.setExplanation(explanation);
        return response;
    }
}
