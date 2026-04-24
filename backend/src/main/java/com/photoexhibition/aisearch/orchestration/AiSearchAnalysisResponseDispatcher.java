package com.photoexhibition.aisearch.orchestration;

import com.photoexhibition.dto.AiSearchResponse;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Supplier;

@Component
public class AiSearchAnalysisResponseDispatcher {

    public AiSearchResponse dispatch(String routingType,
                                     boolean explicitAnchorPerson,
                                     Map<String, Supplier<AiSearchResponse>> handlers) {
        if (routingType == null || routingType.isBlank() || handlers == null || handlers.isEmpty()) {
            throw new IllegalArgumentException("分析响应分发参数无效");
        }

        String effectiveRoutingType = resolveEffectiveRoutingType(routingType, explicitAnchorPerson);
        Supplier<AiSearchResponse> handler = handlers.get(effectiveRoutingType);
        if (handler == null) {
            throw new IllegalArgumentException("不支持的分析类型: " + routingType);
        }
        return handler.get();
    }

    private String resolveEffectiveRoutingType(String routingType, boolean explicitAnchorPerson) {
        if ("person_cooccurrence".equals(routingType) || "person_pair_cooccurrence".equals(routingType)) {
            return explicitAnchorPerson ? "person_cooccurrence" : "person_pair_cooccurrence";
        }
        return routingType;
    }
}
