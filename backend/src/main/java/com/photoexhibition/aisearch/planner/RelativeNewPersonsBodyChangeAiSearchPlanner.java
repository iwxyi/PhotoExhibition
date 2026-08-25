package com.photoexhibition.aisearch.planner;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RelativeNewPersonsBodyChangeAiSearchPlanner {

    public boolean supports(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        RelativeYearExpressionParser.BodyChangeYearRoles roles = RelativeYearExpressionParser.resolveBodyChangeRoles(
            query,
            this::refersToNewness
        );
        return refersToPersons(query)
            && refersToNewness(query)
            && refersToBodyChange(query)
            && roles != null
            && roles.getEndYear() > roles.getStartYear();
    }

    public AiSearchPlan plan(String query, int offset, int limit) {
        RelativeYearExpressionParser.BodyChangeYearRoles roles = RelativeYearExpressionParser.resolveBodyChangeRoles(
            query,
            this::refersToNewness
        );
        if (roles == null || roles.getEndYear() <= roles.getStartYear()) {
            throw new IllegalArgumentException("query does not match relative new persons body change planner");
        }
        return planForYears(query, roles.getTargetYear(), roles.getStartYear(), roles.getEndYear(), offset, limit);
    }

    public AiSearchPlan planForYears(String query, int targetYear, int startYear, int endYear, int offset, int limit) {

        AiSearchPlan plan = new AiSearchPlan();
        plan.setQuery(query);
        plan.setQueryMode("analysis");
        plan.setPlanType("relative_new_persons_body_change");
        plan.setResultTypes(List.of("persons"));
        plan.getMetadata().put("targetYear", targetYear);
        plan.getMetadata().put("startYear", startYear);
        plan.getMetadata().put("endYear", endYear);
        plan.getMetadata().put("desiredTrend", "gained_weight");
        plan.getMetadata().put("intent", "relative_new_persons_body_change");

        plan.getSteps().add(yearFilterStep("target_year_photos", "筛选目标年份中的可见照片", targetYear));
        plan.getSteps().add(endBoundFilterStep("baseline_photos", "筛选目标年份之前的可见照片", targetYear - 1));
        plan.getSteps().add(aggregatePersonsStep("target_year_persons", "target_year_photos", "聚合目标年份出现的人物"));
        plan.getSteps().add(aggregatePersonsStep("baseline_persons", "baseline_photos", "聚合目标年份之前出现的人物"));

        AiSearchPlanStep difference = new AiSearchPlanStep();
        difference.setId("new_persons");
        difference.setOperator("set_difference");
        difference.setInputRef("target_year_persons");
        difference.setOutputKey("new_persons");
        difference.getDependsOn().add("target_year_persons");
        difference.getDependsOn().add("baseline_persons");
        difference.setDescription("保留目标年份首次出现的人物");
        difference.setArgs(Map.of("compareWithRef", "baseline_persons"));
        plan.getSteps().add(difference);

        AiSearchPlanStep derive = new AiSearchPlanStep();
        derive.setId("body_change_new_persons");
        derive.setOperator("derive_candidate_body_change");
        derive.setInputRef("new_persons");
        derive.setOutputKey("body_change_new_persons");
        derive.getDependsOn().add("new_persons");
        derive.setDescription("分析目标年份新认识人物在比较区间内的体型变化");
        derive.setArgs(Map.of(
            "startYear", startYear,
            "endYear", endYear,
            "desiredTrend", "gained_weight"
        ));
        plan.getSteps().add(derive);

        AiSearchPlanStep sort = new AiSearchPlanStep();
        sort.setId("sorted_body_change_new_persons");
        sort.setOperator("sort");
        sort.setInputRef("body_change_new_persons");
        sort.setOutputKey("sorted_body_change_new_persons");
        sort.getDependsOn().add("body_change_new_persons");
        sort.setDescription("按变胖幅度与最近出现时间排序");
        sort.setArgs(Map.of(
            "field", "changePercent",
            "direction", "desc",
            "secondaryField", "matchedLastSeen",
            "secondaryDirection", "desc"
        ));
        plan.getSteps().add(sort);

        AiSearchPlanStep limited = new AiSearchPlanStep();
        limited.setId("limited_body_change_new_persons");
        limited.setOperator("limit");
        limited.setInputRef("sorted_body_change_new_persons");
        limited.setOutputKey("limited_body_change_new_persons");
        limited.getDependsOn().add("sorted_body_change_new_persons");
        limited.setDescription("分页截断目标年份新认识且后续更胖的人物结果");
        limited.setArgs(Map.of(
            "offset", Math.max(0, offset),
            "size", Math.max(1, limit)
        ));
        plan.getSteps().add(limited);

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

    private boolean refersToPersons(String query) {
        return query.contains("谁") || query.contains("哪些人")
            || query.contains("哪些人物") || query.contains("人物") || query.contains("的人");
    }

    private boolean refersToNewness(String query) {
        return query.contains("新认识") || query.contains("新出现")
            || query.contains("第一次出现") || query.contains("首次出现") || query.contains("才出现");
    }

    private boolean refersToBodyChange(String query) {
        return (query.contains("胖") || query.contains("更圆") || query.contains("发福"))
            && (query.contains("比") || query.contains("相比"));
    }
}
