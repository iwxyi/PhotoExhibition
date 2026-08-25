package com.photoexhibition.aisearch.planner;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class RelativeNewPersonsWithScopedPhotosThenPairCooccurrenceAiSearchPlanner {

    private final RelativeNewPersonsWithScopedPhotosAiSearchPlanner basePlanner;

    public RelativeNewPersonsWithScopedPhotosThenPairCooccurrenceAiSearchPlanner(RelativeNewPersonsWithScopedPhotosAiSearchPlanner basePlanner) {
        this.basePlanner = basePlanner;
    }

    public boolean supports(String query, List<String> cameraCandidates, List<String> lensCandidates) {
        if (!basePlanner.supports(query, cameraCandidates, lensCandidates)) {
            return false;
        }
        return refersToPair(query) && refersToLater(query) && refersToPairQuestion(query);
    }

    public AiSearchPlan plan(String query,
                             List<String> cameraCandidates,
                             List<String> lensCandidates,
                             int offset,
                             int limit) {
        AiSearchPlan plan = basePlanner.plan(query, cameraCandidates, lensCandidates, offset, limit);
        plan.setPlanType("relative_new_persons_with_scoped_photos_then_pair_cooccurrence");
        plan.setResultTypes(List.of("albums"));
        plan.getMetadata().put("intent", "relative_new_persons_with_scoped_photos_then_pair_cooccurrence");

        List<AiSearchPlanStep> rewritten = new ArrayList<>();
        for (AiSearchPlanStep step : plan.getSteps()) {
            if ("sorted_scoped_new_persons".equals(step.getId()) || "limited_scoped_new_persons".equals(step.getId())) {
                continue;
            }
            rewritten.add(step);
        }
        plan.setSteps(rewritten);

        AiSearchPlanStep derive = new AiSearchPlanStep();
        derive.setId("relative_scoped_new_person_pairs");
        derive.setOperator("derive_temporal_person_pair_cooccurrence");
        derive.setInputRef("scoped_new_persons");
        derive.setOutputKey("relative_scoped_new_person_pairs");
        derive.getDependsOn().add("scoped_new_persons");
        derive.setDescription("统计范围内新认识人物在首次出现后的高频同框人物组合");
        plan.getSteps().add(derive);

        AiSearchPlanStep sort = new AiSearchPlanStep();
        sort.setId("sorted_relative_scoped_new_person_pairs");
        sort.setOperator("sort");
        sort.setInputRef("relative_scoped_new_person_pairs");
        sort.setOutputKey("sorted_relative_scoped_new_person_pairs");
        sort.getDependsOn().add("relative_scoped_new_person_pairs");
        sort.setDescription("按后续同框频次与最近同框时间排序范围内人物组合");
        sort.setArgs(Map.of(
            "field", "matchedPhotoCount",
            "direction", "desc",
            "secondaryField", "matchedLastSeen",
            "secondaryDirection", "desc"
        ));
        plan.getSteps().add(sort);

        AiSearchPlanStep limited = new AiSearchPlanStep();
        limited.setId("limited_relative_scoped_new_person_pairs");
        limited.setOperator("limit");
        limited.setInputRef("sorted_relative_scoped_new_person_pairs");
        limited.setOutputKey("limited_relative_scoped_new_person_pairs");
        limited.getDependsOn().add("sorted_relative_scoped_new_person_pairs");
        limited.setDescription("分页截断范围内新认识人物后续高频同框组合结果");
        limited.setArgs(Map.of(
            "offset", Math.max(0, offset),
            "size", Math.max(1, limit)
        ));
        plan.getSteps().add(limited);

        return plan;
    }

    private boolean refersToLater(String query) {
        return query != null && (query.contains("后来") || query.contains("之后") || query.contains("后续"));
    }

    private boolean refersToPair(String query) {
        return query != null && (query.contains("同框") || query.contains("一起出现")
            || query.contains("经常一起") || query.contains("共同出现"));
    }

    private boolean refersToPairQuestion(String query) {
        return query != null && (query.contains("谁和谁") || query.contains("哪两位") || query.contains("哪些人最常一起"));
    }
}
