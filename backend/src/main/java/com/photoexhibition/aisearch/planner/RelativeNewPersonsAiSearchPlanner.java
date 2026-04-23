package com.photoexhibition.aisearch.planner;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class RelativeNewPersonsAiSearchPlanner {

    public boolean supports(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        return refersToPersons(query) && refersToNewness(query) && refersToRelativeYear(query);
    }

    public AiSearchPlan plan(String query, int offset, int limit) {
        int currentYear = LocalDate.now().getYear();
        int targetYear = query.contains("前年")
            ? currentYear - 2
            : query.contains("今年") ? currentYear : currentYear - 1;

        String targetStart = targetYear + "-01-01";
        String targetEnd = targetYear + "-12-31";
        String baselineEnd = (targetYear - 1) + "-12-31";

        AiSearchPlan plan = new AiSearchPlan();
        plan.setQuery(query);
        plan.setQueryMode("analysis");
        plan.setPlanType("relative_new_persons");
        plan.setResultTypes(List.of("persons"));
        plan.getMetadata().put("targetYear", targetYear);
        plan.getMetadata().put("intent", "relative_new_persons");

        AiSearchPlanStep targetRange = new AiSearchPlanStep();
        targetRange.setId("target_range_photos");
        targetRange.setOperator("filter_photos");
        targetRange.setOutputKey("target_range_photos");
        targetRange.setDescription("筛选目标年份中的可见照片");
        targetRange.setArgs(Map.of(
            "startDate", targetStart,
            "endDate", targetEnd
        ));
        plan.getSteps().add(targetRange);

        AiSearchPlanStep baselineRange = new AiSearchPlanStep();
        baselineRange.setId("baseline_photos");
        baselineRange.setOperator("filter_photos");
        baselineRange.setOutputKey("baseline_photos");
        baselineRange.setDescription("筛选目标年份之前的可见照片");
        baselineRange.setArgs(Map.of("endDate", baselineEnd));
        plan.getSteps().add(baselineRange);

        AiSearchPlanStep targetPersons = new AiSearchPlanStep();
        targetPersons.setId("target_persons");
        targetPersons.setOperator("aggregate_persons");
        targetPersons.setInputRef("target_range_photos");
        targetPersons.setOutputKey("target_persons");
        targetPersons.getDependsOn().add("target_range_photos");
        targetPersons.setDescription("聚合目标年份的人物出现情况");
        plan.getSteps().add(targetPersons);

        AiSearchPlanStep baselinePersons = new AiSearchPlanStep();
        baselinePersons.setId("baseline_persons");
        baselinePersons.setOperator("aggregate_persons");
        baselinePersons.setInputRef("baseline_photos");
        baselinePersons.setOutputKey("baseline_persons");
        baselinePersons.getDependsOn().add("baseline_photos");
        baselinePersons.setDescription("聚合历史人物出现情况");
        plan.getSteps().add(baselinePersons);

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

        AiSearchPlanStep sorted = new AiSearchPlanStep();
        sorted.setId("sorted_new_persons");
        sorted.setOperator("sort");
        sorted.setInputRef("new_persons");
        sorted.setOutputKey("sorted_new_persons");
        sorted.getDependsOn().add("new_persons");
        sorted.setDescription("按目标年份出现频次与最近出现时间排序");
        sorted.setArgs(Map.of(
            "field", "matchedPhotoCount",
            "direction", "desc",
            "secondaryField", "matchedLastSeen",
            "secondaryDirection", "desc"
        ));
        plan.getSteps().add(sorted);

        AiSearchPlanStep limited = new AiSearchPlanStep();
        limited.setId("limited_new_persons");
        limited.setOperator("limit");
        limited.setInputRef("sorted_new_persons");
        limited.setOutputKey("limited_new_persons");
        limited.getDependsOn().add("sorted_new_persons");
        limited.setDescription("分页截断新认识人物结果");
        limited.setArgs(Map.of(
            "offset", Math.max(0, offset),
            "size", Math.max(1, limit)
        ));
        plan.getSteps().add(limited);

        return plan;
    }

    private boolean refersToPersons(String query) {
        return query.contains("谁") || query.contains("有谁") || query.contains("哪些人")
            || query.contains("哪些人物") || query.contains("人物");
    }

    private boolean refersToNewness(String query) {
        return query.contains("新认识") || query.contains("新出现") || query.contains("第一次出现")
            || query.contains("首次出现") || query.contains("才出现");
    }

    private boolean refersToRelativeYear(String query) {
        return query.contains("去年") || query.contains("前年") || query.contains("今年");
    }
}
