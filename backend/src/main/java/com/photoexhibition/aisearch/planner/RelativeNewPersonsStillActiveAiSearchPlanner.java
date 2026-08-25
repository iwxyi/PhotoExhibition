package com.photoexhibition.aisearch.planner;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RelativeNewPersonsStillActiveAiSearchPlanner {

    public boolean supports(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        RelativeYearExpressionParser.ActivityYearRoles roles = RelativeYearExpressionParser.resolveActivityRoles(
            query,
            this::refersToNewness,
            this::refersToStillActive
        );
        return refersToPersons(query)
            && refersToNewness(query)
            && refersToStillActive(query)
            && roles != null
            && roles.getActiveYear() > roles.getTargetYear();
    }

    public AiSearchPlan plan(String query, int offset, int limit) {
        RelativeYearExpressionParser.ActivityYearRoles roles = RelativeYearExpressionParser.resolveActivityRoles(
            query,
            this::refersToNewness,
            this::refersToStillActive
        );
        if (roles == null || roles.getActiveYear() <= roles.getTargetYear()) {
            throw new IllegalArgumentException("query does not match relative new persons still active planner");
        }
        return planForYears(query, roles.getTargetYear(), roles.getActiveYear(), offset, limit);
    }

    public AiSearchPlan planForYears(String query, int targetYear, int activeYear, int offset, int limit) {
        int baselineYear = targetYear - 1;

        AiSearchPlan plan = new AiSearchPlan();
        plan.setQuery(query);
        plan.setQueryMode("analysis");
        plan.setPlanType("relative_new_persons_still_active");
        plan.setResultTypes(List.of("persons"));
        plan.getMetadata().put("targetYear", targetYear);
        plan.getMetadata().put("activeYear", activeYear);
        plan.getMetadata().put("baselineYear", baselineYear);
        plan.getMetadata().put("intent", "relative_new_persons_still_active");

        plan.getSteps().add(yearFilterStep("target_year_photos", "筛选目标年份中的可见照片", targetYear));
        plan.getSteps().add(endBoundFilterStep("baseline_photos", "筛选目标年份之前的可见照片", targetYear - 1));
        plan.getSteps().add(yearFilterStep("active_year_photos", "筛选活跃年份中的可见照片", activeYear));

        plan.getSteps().add(aggregatePersonsStep("target_year_persons", "target_year_photos", "聚合目标年份出现的人物"));
        plan.getSteps().add(aggregatePersonsStep("baseline_persons", "baseline_photos", "聚合目标年份之前出现的人物"));
        plan.getSteps().add(aggregatePersonsStep("active_year_persons", "active_year_photos", "聚合活跃年份出现的人物"));

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

        AiSearchPlanStep intersection = new AiSearchPlanStep();
        intersection.setId("still_active_new_persons");
        intersection.setOperator("set_intersection");
        intersection.setInputRef("active_year_persons");
        intersection.setOutputKey("still_active_new_persons");
        intersection.getDependsOn().add("active_year_persons");
        intersection.getDependsOn().add("new_persons");
        intersection.setDescription("保留目标年份新认识且活跃年份仍有出现的人物");
        intersection.setArgs(Map.of("compareWithRef", "new_persons"));
        plan.getSteps().add(intersection);

        AiSearchPlanStep sorted = new AiSearchPlanStep();
        sorted.setId("sorted_still_active_new_persons");
        sorted.setOperator("sort");
        sorted.setInputRef("still_active_new_persons");
        sorted.setOutputKey("sorted_still_active_new_persons");
        sorted.getDependsOn().add("still_active_new_persons");
        sorted.setDescription("按活跃年份出现频次与最近出现时间排序");
        sorted.setArgs(Map.of(
            "field", "matchedPhotoCount",
            "direction", "desc",
            "secondaryField", "matchedLastSeen",
            "secondaryDirection", "desc"
        ));
        plan.getSteps().add(sorted);

        AiSearchPlanStep limited = new AiSearchPlanStep();
        limited.setId("limited_still_active_new_persons");
        limited.setOperator("limit");
        limited.setInputRef("sorted_still_active_new_persons");
        limited.setOutputKey("limited_still_active_new_persons");
        limited.getDependsOn().add("sorted_still_active_new_persons");
        limited.setDescription("分页截断活跃年份仍持续出现的新认识人物结果");
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
            || query.contains("哪些人物") || query.contains("人物");
    }

    private boolean refersToNewness(String query) {
        return query.contains("新认识") || query.contains("新出现")
            || query.contains("第一次出现") || query.contains("首次出现") || query.contains("才出现");
    }

    private boolean refersToStillActive(String query) {
        return query.contains("还经常出现") || query.contains("还常出现")
            || query.contains("还出现") || query.contains("仍然出现") || query.contains("今年还有");
    }
}
