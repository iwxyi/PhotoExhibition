package com.photoexhibition.aisearch.planner;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class RelativeNewPersonsThenPairCooccurrenceAiSearchPlanner {

    public boolean supports(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        return query.contains("去年")
            && refersToNewness(query)
            && refersToLater(query)
            && refersToPair(query)
            && refersToPairQuestion(query);
    }

    public AiSearchPlan plan(String query, int offset, int limit) {
        int currentYear = LocalDate.now().getYear();
        int targetYear = currentYear - 1;
        return planForYear(query, targetYear, offset, limit);
    }

    public AiSearchPlan planForYear(String query, int targetYear, int offset, int limit) {
        AiSearchPlan plan = new AiSearchPlan();
        plan.setQuery(query);
        plan.setQueryMode("analysis");
        plan.setPlanType("relative_new_persons_then_pair_cooccurrence");
        plan.setResultTypes(List.of("albums"));
        plan.getMetadata().put("targetYear", targetYear);
        plan.getMetadata().put("intent", "relative_new_persons_then_pair_cooccurrence");

        plan.getSteps().add(yearFilterStep("target_year_photos", "筛选去年中的可见照片", targetYear));
        plan.getSteps().add(endBoundFilterStep("baseline_photos", "筛选去年之前的可见照片", targetYear - 1));
        plan.getSteps().add(aggregatePersonsStep("target_year_persons", "target_year_photos", "聚合去年出现的人物"));
        plan.getSteps().add(aggregatePersonsStep("baseline_persons", "baseline_photos", "聚合去年之前出现的人物"));

        AiSearchPlanStep difference = new AiSearchPlanStep();
        difference.setId("new_persons");
        difference.setOperator("set_difference");
        difference.setInputRef("target_year_persons");
        difference.setOutputKey("new_persons");
        difference.getDependsOn().add("target_year_persons");
        difference.getDependsOn().add("baseline_persons");
        difference.setDescription("保留去年首次出现的人物");
        difference.setArgs(Map.of("compareWithRef", "baseline_persons"));
        plan.getSteps().add(difference);

        AiSearchPlanStep derive = new AiSearchPlanStep();
        derive.setId("relative_new_person_pairs");
        derive.setOperator("derive_temporal_person_pair_cooccurrence");
        derive.setInputRef("new_persons");
        derive.setOutputKey("relative_new_person_pairs");
        derive.getDependsOn().add("new_persons");
        derive.setDescription("统计新认识人物在首次出现后的高频同框人物组合");
        plan.getSteps().add(derive);

        AiSearchPlanStep sort = new AiSearchPlanStep();
        sort.setId("sorted_relative_new_person_pairs");
        sort.setOperator("sort");
        sort.setInputRef("relative_new_person_pairs");
        sort.setOutputKey("sorted_relative_new_person_pairs");
        sort.getDependsOn().add("relative_new_person_pairs");
        sort.setDescription("按后续同框频次与最近同框时间排序人物组合");
        sort.setArgs(Map.of(
            "field", "matchedPhotoCount",
            "direction", "desc",
            "secondaryField", "matchedLastSeen",
            "secondaryDirection", "desc"
        ));
        plan.getSteps().add(sort);

        AiSearchPlanStep limitStep = new AiSearchPlanStep();
        limitStep.setId("limited_relative_new_person_pairs");
        limitStep.setOperator("limit");
        limitStep.setInputRef("sorted_relative_new_person_pairs");
        limitStep.setOutputKey("limited_relative_new_person_pairs");
        limitStep.getDependsOn().add("sorted_relative_new_person_pairs");
        limitStep.setDescription("分页截断新认识人物后续高频同框组合结果");
        limitStep.setArgs(Map.of(
            "offset", Math.max(0, offset),
            "size", Math.max(1, limit)
        ));
        plan.getSteps().add(limitStep);

        return plan;
    }

    private AiSearchPlanStep yearFilterStep(String id, String description, int year) {
        AiSearchPlanStep step = new AiSearchPlanStep();
        step.setId(id);
        step.setOperator("filter_photos");
        step.setOutputKey(id);
        step.setDescription(description);
        step.setArgs(Map.of(
            "startDate", year + "-01-01",
            "endDate", year + "-12-31"
        ));
        return step;
    }

    private AiSearchPlanStep endBoundFilterStep(String id, String description, int endYear) {
        AiSearchPlanStep step = new AiSearchPlanStep();
        step.setId(id);
        step.setOperator("filter_photos");
        step.setOutputKey(id);
        step.setDescription(description);
        step.setArgs(Map.of("endDate", endYear + "-12-31"));
        return step;
    }

    private AiSearchPlanStep aggregatePersonsStep(String id, String inputRef, String description) {
        AiSearchPlanStep step = new AiSearchPlanStep();
        step.setId(id);
        step.setOperator("aggregate_persons");
        step.setInputRef(inputRef);
        step.setOutputKey(id);
        step.getDependsOn().add(inputRef);
        step.setDescription(description);
        return step;
    }

    private boolean refersToNewness(String query) {
        return query.contains("新认识") || query.contains("新出现")
            || query.contains("第一次出现") || query.contains("首次出现") || query.contains("才出现");
    }

    private boolean refersToLater(String query) {
        return query.contains("后来") || query.contains("之后") || query.contains("后续");
    }

    private boolean refersToPair(String query) {
        return query.contains("同框") || query.contains("一起出现")
            || query.contains("经常一起") || query.contains("共同出现");
    }

    private boolean refersToPairQuestion(String query) {
        return query.contains("谁和谁") || query.contains("哪两位") || query.contains("哪些人最常一起");
    }
}
