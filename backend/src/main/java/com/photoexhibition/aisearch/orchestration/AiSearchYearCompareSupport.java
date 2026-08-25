package com.photoexhibition.aisearch.orchestration;

import com.photoexhibition.aisearch.executor.AiSearchExecutionContext;
import com.photoexhibition.aisearch.executor.AiSearchExecutionResult;
import com.photoexhibition.aisearch.executor.AiSearchPlanExecutor;
import com.photoexhibition.aisearch.plan.AiSearchPlan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiSearchYearCompareSupport {

    public YearComparison resolveComparison(String query, int currentYear) {
        if (query != null) {
            if (query.contains("今年") && query.contains("去年")) {
                return new YearComparison(currentYear, currentYear - 1);
            }
            if (query.contains("去年") && query.contains("前年")) {
                return new YearComparison(currentYear - 1, currentYear - 2);
            }
            if (query.contains("今年") && query.contains("前年")) {
                return new YearComparison(currentYear, currentYear - 2);
            }
            if (query.contains("去年")) {
                return new YearComparison(currentYear - 1, currentYear - 2);
            }
        }
        return new YearComparison(currentYear, currentYear - 1);
    }

    public String stripComparisonNoise(String query, java.util.function.Function<String, String> stripAnalysisNoise) {
        if (query == null) {
            return "";
        }
        String result = query
            .replace("去年和前年相比", " ")
            .replace("今年和去年相比", " ")
            .replace("和前年相比", " ")
            .replace("和去年相比", " ")
            .replace("相比", " ")
            .replace("对比", " ")
            .replace("比较", " ")
            .replace("更多还是更少", " ")
            .replace("更少还是更多", " ")
            .replace("更多还是少", " ");
        return stripAnalysisNoise.apply(result);
    }

    public AiSearchExecutionResult executePlan(AiSearchPlanExecutor executor,
                                               AiSearchPlan plan,
                                               List<Long> leftPhotoIds,
                                               List<Long> rightPhotoIds) {
        AiSearchExecutionContext context = new AiSearchExecutionContext();
        context.setQuery(plan.getQuery());
        context.setQueryMode(plan.getQueryMode());
        context.getValues().put("left_period_photo_ids", safePhotoIds(leftPhotoIds));
        context.getValues().put("right_period_photo_ids", safePhotoIds(rightPhotoIds));

        AiSearchExecutionResult executionResult = executor.execute(plan, context);

        @SuppressWarnings("unchecked")
        Map<String, Object> comparison = (Map<String, Object>) executionResult.getFinalOutputs()
            .getOrDefault("period_comparison", Collections.emptyMap());
        Map<String, Object> metrics = new LinkedHashMap<>(comparison);
        metrics.put("leftYear", plan.getMetadata().get("leftYear"));
        metrics.put("rightYear", plan.getMetadata().get("rightYear"));
        metrics.put("subject", plan.getMetadata().get("subject"));
        executionResult.getFinalOutputs().put("year_compare_metrics", metrics);
        return executionResult;
    }

    public Map<String, Object> buildAnalysisData(YearComparison comparison,
                                                 AiSearchExecutionResult executionResult,
                                                 String answer) {
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) executionResult.getFinalOutputs()
            .getOrDefault("year_compare_metrics", Collections.emptyMap());
        Map<String, Object> analysisData = new LinkedHashMap<>();
        analysisData.put("analysisType", "year_compare");
        analysisData.put("leftYear", comparison.getLeftYear());
        analysisData.put("rightYear", comparison.getRightYear());
        analysisData.put("leftCount", metrics.get("leftCount"));
        analysisData.put("rightCount", metrics.get("rightCount"));
        analysisData.put("difference", metrics.get("difference"));
        analysisData.put("changeRatio", metrics.get("changeRatio"));
        analysisData.put("subject", metrics.get("subject"));
        analysisData.put("trend", metrics.get("trend"));
        analysisData.put("conclusion", answer);
        return analysisData;
    }

    private List<Long> safePhotoIds(List<Long> photoIds) {
        if (photoIds == null || photoIds.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(photoIds);
    }

    public static class YearComparison {
        private final int leftYear;
        private final int rightYear;

        public YearComparison(int leftYear, int rightYear) {
            this.leftYear = leftYear;
            this.rightYear = rightYear;
        }

        public int getLeftYear() {
            return leftYear;
        }

        public int getRightYear() {
            return rightYear;
        }
    }
}
