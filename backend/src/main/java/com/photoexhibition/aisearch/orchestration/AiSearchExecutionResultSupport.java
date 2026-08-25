package com.photoexhibition.aisearch.orchestration;

import com.photoexhibition.aisearch.executor.AiSearchExecutionContext;
import com.photoexhibition.aisearch.executor.AiSearchExecutionResult;
import com.photoexhibition.aisearch.executor.AiSearchPlanExecutor;
import com.photoexhibition.aisearch.model.AiSearchPersonAggregate;
import com.photoexhibition.aisearch.model.AiSearchPersonGrowthAggregate;
import com.photoexhibition.aisearch.model.AiSearchPersonPairAggregate;
import com.photoexhibition.aisearch.plan.AiSearchPlan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
public class AiSearchExecutionResultSupport {

    public AiSearchExecutionResult executeMatchedPhotoOverviewPlan(AiSearchPlanExecutor executor,
                                                                   String query,
                                                                   AiSearchPlan plan,
                                                                   List<Long> matchedPhotoIds,
                                                                   String metricsKey,
                                                                   Function<AiSearchExecutionResult, Map<String, Object>> metricsBuilder) {
        AiSearchExecutionContext context = new AiSearchExecutionContext();
        context.setQuery(query);
        context.setQueryMode(plan.getQueryMode());
        context.getValues().put("matched_photo_ids", matchedPhotoIds == null ? Collections.emptyList() : new ArrayList<>(matchedPhotoIds));

        AiSearchExecutionResult executionResult = executor.execute(plan, context);
        executionResult.getFinalOutputs().put(metricsKey, metricsBuilder.apply(executionResult));
        return executionResult;
    }

    public Map<String, Object> extractMetrics(AiSearchExecutionResult executionResult, String metricsKey) {
        Object output = executionResult == null ? null : executionResult.getFinalOutputs().get(metricsKey);
        if (!(output instanceof Map<?, ?>)) {
            return Collections.emptyMap();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) output;
        return metrics;
    }

    public List<AiSearchPersonAggregate> extractPersonAggregates(AiSearchExecutionResult executionResult, String outputKey) {
        return extractTypedList(executionResult, outputKey, AiSearchPersonAggregate.class);
    }

    public List<AiSearchPersonPairAggregate> extractPersonPairAggregates(AiSearchExecutionResult executionResult, String outputKey) {
        return extractTypedList(executionResult, outputKey, AiSearchPersonPairAggregate.class);
    }

    public List<AiSearchPersonGrowthAggregate> extractPersonGrowthAggregates(AiSearchExecutionResult executionResult, String outputKey) {
        return extractTypedList(executionResult, outputKey, AiSearchPersonGrowthAggregate.class);
    }

    private <T> List<T> extractTypedList(AiSearchExecutionResult executionResult, String outputKey, Class<T> type) {
        Object output = executionResult == null ? null : executionResult.getFinalOutputs().get(outputKey);
        if (!(output instanceof List<?>)) {
            return Collections.emptyList();
        }
        List<?> rows = (List<?>) output;
        List<T> aggregates = new ArrayList<>();
        for (Object row : rows) {
            if (type.isInstance(row)) {
                aggregates.add(type.cast(row));
            }
        }
        return aggregates;
    }
}
