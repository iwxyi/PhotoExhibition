package com.photoexhibition.aisearch.validation;

import com.photoexhibition.dto.AiSearchIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class AiSearchAnalysisFallbackIntentFactory {

    public AiSearchIntent build(String routingType,
                                AiSearchAnalysisFallbackRequest request,
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
        if (routingType == null || request == null || request.getResolvedQuery() == null) {
            return null;
        }
        String resolvedQuery = request.getResolvedQuery();
        switch (routingType) {
            case "location":
                return locationIntentBuilder.apply(resolvedQuery);
            case "album":
                return albumIntentBuilder.apply(resolvedQuery);
            case "month":
                return monthIntentBuilder.apply(resolvedQuery);
            case "count":
                return countIntentBuilder.apply(resolvedQuery);
            case "person":
                return personIntentBuilder.apply(resolvedQuery);
            case "person_cooccurrence":
                return personCooccurrenceIntentBuilder.apply(resolvedQuery);
            case "person_pair_cooccurrence":
                return personPairIntentBuilder.apply(resolvedQuery);
            case "day":
                return dayIntentBuilder.apply(resolvedQuery);
            case "tag":
                return tagIntentBuilder.apply(resolvedQuery);
            case "theme":
                return themeIntentBuilder.apply(resolvedQuery);
            case "year_compare":
                return yearCompareIntentBuilder.apply(resolvedQuery);
            default:
                return null;
        }
    }
}
