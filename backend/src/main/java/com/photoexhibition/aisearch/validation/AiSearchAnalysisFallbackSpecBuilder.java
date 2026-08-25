package com.photoexhibition.aisearch.validation;

import com.photoexhibition.dto.AiSearchAnalysisOperation;
import com.photoexhibition.dto.AiSearchAnalysisScope;
import com.photoexhibition.dto.AiSearchAnalysisSpec;
import com.photoexhibition.dto.AiSearchAnalysisSubject;
import com.photoexhibition.dto.AiSearchIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiSearchAnalysisFallbackSpecBuilder {

    public AiSearchIntent build(AiSearchAnalysisFallbackRequest request, AiSearchIntent baseIntent) {
        if (request == null || request.getRoutingType() == null || request.getRoutingType().isBlank() || baseIntent == null) {
            return null;
        }
        String routingType = request.getRoutingType();

        AiSearchAnalysisOperation operation = new AiSearchAnalysisOperation();
        switch (routingType) {
            case "location":
                operation.setType("location_overview");
                break;
            case "album":
                operation.setType("album_overview");
                break;
            case "month":
                operation.setType("month_overview");
                break;
            case "count":
                operation.setType("count_overview");
                break;
            case "person":
                operation.setType("person_overview");
                break;
            case "person_cooccurrence":
                if (!request.isExplicitAnchorPerson()) {
                    return null;
                }
                operation.setType("person_cooccurrence");
                break;
            case "person_pair_cooccurrence":
                if (request.isExplicitAnchorPerson()) {
                    return null;
                }
                operation.setType("person_pair_cooccurrence");
                break;
            case "day":
                operation.setType("day_overview");
                break;
            case "tag":
                operation.setType("tag_overview");
                break;
            case "theme":
                operation.setType("theme_overview");
                break;
            case "year_compare":
                if (request.getLeftYear() == null || request.getRightYear() == null) {
                    return null;
                }
                operation.setType("year_compare");
                operation.setLeftYear(request.getLeftYear());
                operation.setRightYear(request.getRightYear());
                operation.setSubject(request.getKeywordSummary());
                break;
            default:
                return null;
        }

        AiSearchAnalysisSubject subject = new AiSearchAnalysisSubject();
        subject.setType("filtered_scope");
        AiSearchAnalysisScope scope = new AiSearchAnalysisScope();
        scope.setType("none");

        AiSearchAnalysisSpec spec = new AiSearchAnalysisSpec();
        spec.setSubjectType("persons");
        spec.setSubject(subject);
        spec.setOperation(operation);
        spec.setScope(scope);
        baseIntent.setAnalysisSpec(spec);
        return baseIntent;
    }
}
