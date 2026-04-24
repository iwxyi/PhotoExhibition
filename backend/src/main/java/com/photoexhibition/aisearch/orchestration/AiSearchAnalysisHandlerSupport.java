package com.photoexhibition.aisearch.orchestration;

import com.photoexhibition.dto.AiSearchResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class AiSearchAnalysisHandlerSupport {

    public Map<String, Supplier<AiSearchResponse>> buildHandlers(HandlerCallbacks callbacks) {
        Map<String, Supplier<AiSearchResponse>> handlers = new LinkedHashMap<>();
        handlers.put("theme", callbacks::buildThemeOverviewResponse);
        handlers.put("location", callbacks::buildLocationOverviewResponse);
        handlers.put("album", callbacks::buildAlbumOverviewResponse);
        handlers.put("month", callbacks::buildMonthOverviewResponse);
        handlers.put("count", callbacks::buildCountOverviewResponse);
        handlers.put("person_cooccurrence", callbacks::buildPersonCooccurrenceResponse);
        handlers.put("person_pair_cooccurrence", callbacks::buildPersonPairCooccurrenceResponse);
        handlers.put("person", callbacks::buildPersonOverviewResponse);
        handlers.put("day", callbacks::buildDayOverviewResponse);
        handlers.put("tag", callbacks::buildTagOverviewResponse);
        handlers.put("year_compare", callbacks::buildYearCompareResponse);
        handlers.put("body_change", callbacks::buildBodyChangeResponse);
        return handlers;
    }

    public interface HandlerCallbacks {
        AiSearchResponse buildThemeOverviewResponse();
        AiSearchResponse buildLocationOverviewResponse();
        AiSearchResponse buildAlbumOverviewResponse();
        AiSearchResponse buildMonthOverviewResponse();
        AiSearchResponse buildCountOverviewResponse();
        AiSearchResponse buildPersonCooccurrenceResponse();
        AiSearchResponse buildPersonPairCooccurrenceResponse();
        AiSearchResponse buildPersonOverviewResponse();
        AiSearchResponse buildDayOverviewResponse();
        AiSearchResponse buildTagOverviewResponse();
        AiSearchResponse buildYearCompareResponse();
        AiSearchResponse buildBodyChangeResponse();
    }
}
