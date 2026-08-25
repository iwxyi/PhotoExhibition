package com.photoexhibition.aisearch.planner;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class RelativeNewPersonsThenCooccurrenceAiSearchPlanner {

    public boolean supports(String query, boolean hasAnchorPerson) {
        if (query == null || query.isBlank()) {
            return false;
        }
        return hasAnchorPerson
            && refersToPersons(query)
            && refersToNewness(query)
            && refersToRelativeYear(query)
            && refersToLater(query)
            && refersToCooccurrence(query);
    }

    public AiSearchPlan plan(String query, Long anchorPersonId, String anchorPersonName, int offset, int limit) {
        int currentYear = LocalDate.now().getYear();
        int targetYear = query.contains("前年")
            ? currentYear - 2
            : query.contains("今年") ? currentYear : currentYear - 1;
        return planForYears(query, targetYear, anchorPersonId, anchorPersonName, offset, limit);
    }

    public AiSearchPlan planForYears(String query,
                                     int targetYear,
                                     Long anchorPersonId,
                                     String anchorPersonName,
                                     int offset,
                                     int limit) {
        String targetStart = targetYear + "-01-01";
        String targetEnd = targetYear + "-12-31";
        String baselineEnd = (targetYear - 1) + "-12-31";

        AiSearchPlan plan = new AiSearchPlan();
        plan.setQuery(query);
        plan.setQueryMode("analysis");
        plan.setPlanType("relative_new_persons_then_cooccurrence");
        plan.setResultTypes(List.of("persons"));
        plan.getMetadata().put("targetYear", targetYear);
        plan.getMetadata().put("anchorPersonId", anchorPersonId);
        plan.getMetadata().put("anchorPersonName", anchorPersonName);
        plan.getMetadata().put("intent", "relative_new_persons_then_cooccurrence");

        plan.getSteps().add(yearFilterStep(
            "target_range_photos",
            "筛选目标年份中的可见照片",
            targetStart,
            targetEnd
        ));
        plan.getSteps().add(endBoundFilterStep(
            "baseline_photos",
            "筛选目标年份之前的可见照片",
            baselineEnd
        ));

        plan.getSteps().add(aggregatePersonsStep(
            "target_persons",
            "target_range_photos",
            "聚合目标年份的人物出现情况"
        ));
        plan.getSteps().add(aggregatePersonsStep(
            "baseline_persons",
            "baseline_photos",
            "聚合历史人物出现情况"
        ));

        AiSearchPlanStep difference = new AiSearchPlanStep();
        difference.setId("new_persons");
        difference.setOperator("set_difference");
        difference.setInputRef("target_persons");
        difference.setOutputKey("new_persons");
        difference.getDependsOn().add("target_persons");
        difference.getDependsOn().add("baseline_persons");
        difference.setDescription("剔除目标年份之前已经出现过的人物");
        difference.setArgs(Map.of("compareWithRef", "baseline_persons"));
        plan.getSteps().add(difference);

        AiSearchPlanStep derive = new AiSearchPlanStep();
        derive.setId("relative_new_persons_cooccurrence");
        derive.setOperator("derive_temporal_person_cooccurrence");
        derive.setInputRef("new_persons");
        derive.setOutputKey("relative_new_persons_cooccurrence");
        derive.getDependsOn().add("new_persons");
        derive.setDescription("统计这些新认识人物在首次出现之后与锚点人物的共同出现频率");
        derive.setArgs(Map.of("anchorPersonId", anchorPersonId));
        plan.getSteps().add(derive);

        AiSearchPlanStep sort = new AiSearchPlanStep();
        sort.setId("sorted_relative_new_persons_cooccurrence");
        sort.setOperator("sort");
        sort.setInputRef("relative_new_persons_cooccurrence");
        sort.setOutputKey("sorted_relative_new_persons_cooccurrence");
        sort.getDependsOn().add("relative_new_persons_cooccurrence");
        sort.setDescription("按后续同框频次与最近同框时间排序");
        sort.setArgs(Map.of(
            "field", "matchedPhotoCount",
            "direction", "desc",
            "secondaryField", "matchedLastSeen",
            "secondaryDirection", "desc"
        ));
        plan.getSteps().add(sort);

        AiSearchPlanStep limitStep = new AiSearchPlanStep();
        limitStep.setId("limited_relative_new_persons_cooccurrence");
        limitStep.setOperator("limit");
        limitStep.setInputRef("sorted_relative_new_persons_cooccurrence");
        limitStep.setOutputKey("limited_relative_new_persons_cooccurrence");
        limitStep.getDependsOn().add("sorted_relative_new_persons_cooccurrence");
        limitStep.setDescription("分页截断新认识人物的后续同框结果");
        limitStep.setArgs(Map.of(
            "offset", Math.max(0, offset),
            "size", Math.max(1, limit)
        ));
        plan.getSteps().add(limitStep);

        return plan;
    }

    private AiSearchPlanStep yearFilterStep(String id, String description, String startDate, String endDate) {
        AiSearchPlanStep step = new AiSearchPlanStep();
        step.setId(id);
        step.setOperator("filter_photos");
        step.setOutputKey(id);
        step.setDescription(description);
        step.setArgs(Map.of(
            "startDate", startDate,
            "endDate", endDate
        ));
        return step;
    }

    private AiSearchPlanStep endBoundFilterStep(String id, String description, String endDate) {
        AiSearchPlanStep step = new AiSearchPlanStep();
        step.setId(id);
        step.setOperator("filter_photos");
        step.setOutputKey(id);
        step.setDescription(description);
        step.setArgs(Map.of("endDate", endDate));
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

    private boolean refersToPersons(String query) {
        return query.contains("谁") || query.contains("哪些人")
            || query.contains("哪些人物") || query.contains("人物");
    }

    private boolean refersToNewness(String query) {
        return query.contains("新认识") || query.contains("新出现")
            || query.contains("第一次出现") || query.contains("首次出现") || query.contains("才出现");
    }

    private boolean refersToRelativeYear(String query) {
        return query.contains("去年") || query.contains("前年") || query.contains("今年");
    }

    private boolean refersToLater(String query) {
        return query.contains("后来") || query.contains("之后") || query.contains("后续");
    }

    private boolean refersToCooccurrence(String query) {
        return query.contains("同框") || query.contains("一起出现")
            || query.contains("经常一起") || query.contains("共同出现");
    }
}
