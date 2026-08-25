package com.photoexhibition.aisearch.planner;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TemporalPersonSetAiSearchPlanner {

    public boolean supports(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        RelativeYearExpressionParser.TemporalYearRoles roles = RelativeYearExpressionParser.resolveTemporalSetRoles(query);
        return refersToPersons(query)
            && refersToAbsent(query)
            && refersToPresent(query)
            && refersToDisappearAgain(query)
            && roles != null
            && roles.getAbsentYear() < roles.getPresentYear()
            && roles.getPresentYear() < roles.getMissingAgainYear();
    }

    public AiSearchPlan plan(String query, int offset, int limit) {
        RelativeYearExpressionParser.TemporalYearRoles roles = RelativeYearExpressionParser.resolveTemporalSetRoles(query);
        if (roles == null || roles.getAbsentYear() >= roles.getPresentYear()
            || roles.getPresentYear() >= roles.getMissingAgainYear()) {
            throw new IllegalArgumentException("query does not match temporal person set planner");
        }
        return planForYears(query, roles.getAbsentYear(), roles.getPresentYear(), roles.getMissingAgainYear(), offset, limit);
    }

    public AiSearchPlan planForYears(String query,
                                     int absentYear,
                                     int presentYear,
                                     int missingAgainYear,
                                     int offset,
                                     int limit) {

        AiSearchPlan plan = new AiSearchPlan();
        plan.setQuery(query);
        plan.setQueryMode("analysis");
        plan.setPlanType("temporal_person_set");
        plan.setResultTypes(List.of("persons"));
        plan.getMetadata().put("absentYear", absentYear);
        plan.getMetadata().put("presentYear", presentYear);
        plan.getMetadata().put("missingAgainYear", missingAgainYear);
        plan.getMetadata().put("intent", "temporal_person_set");

        plan.getSteps().add(yearFilterStep(
            "absent_year_photos",
            "筛选缺席年份中的可见照片",
            absentYear
        ));
        plan.getSteps().add(yearFilterStep(
            "present_year_photos",
            "筛选出现年份中的可见照片",
            presentYear
        ));
        plan.getSteps().add(yearFilterStep(
            "missing_again_year_photos",
            "筛选再次缺席判断年份中的可见照片",
            missingAgainYear
        ));

        plan.getSteps().add(aggregatePersonsStep(
            "absent_year_persons",
            "absent_year_photos",
            "聚合缺席年份出现的人物"
        ));
        plan.getSteps().add(aggregatePersonsStep(
            "present_year_persons",
            "present_year_photos",
            "聚合出现年份出现的人物"
        ));
        plan.getSteps().add(aggregatePersonsStep(
            "missing_again_year_persons",
            "missing_again_year_photos",
            "聚合再次缺席判断年份出现的人物"
        ));

        AiSearchPlanStep newlyAppeared = new AiSearchPlanStep();
        newlyAppeared.setId("newly_appeared_persons");
        newlyAppeared.setOperator("set_difference");
        newlyAppeared.setInputRef("present_year_persons");
        newlyAppeared.setOutputKey("newly_appeared_persons");
        newlyAppeared.getDependsOn().add("present_year_persons");
        newlyAppeared.getDependsOn().add("absent_year_persons");
        newlyAppeared.setDescription("保留出现年份出现、缺席年份未出现的人物");
        newlyAppeared.setArgs(Map.of("compareWithRef", "absent_year_persons"));
        plan.getSteps().add(newlyAppeared);

        AiSearchPlanStep disappearedAgain = new AiSearchPlanStep();
        disappearedAgain.setId("temporal_person_set_candidates");
        disappearedAgain.setOperator("set_difference");
        disappearedAgain.setInputRef("newly_appeared_persons");
        disappearedAgain.setOutputKey("temporal_person_set_candidates");
        disappearedAgain.getDependsOn().add("newly_appeared_persons");
        disappearedAgain.getDependsOn().add("missing_again_year_persons");
        disappearedAgain.setDescription("剔除再次缺席判断年份又出现的人物，保留未再出现的人");
        disappearedAgain.setArgs(Map.of("compareWithRef", "missing_again_year_persons"));
        plan.getSteps().add(disappearedAgain);

        AiSearchPlanStep sorted = new AiSearchPlanStep();
        sorted.setId("sorted_temporal_person_set");
        sorted.setOperator("sort");
        sorted.setInputRef("temporal_person_set_candidates");
        sorted.setOutputKey("sorted_temporal_person_set");
        sorted.getDependsOn().add("temporal_person_set_candidates");
        sorted.setDescription("按出现年份出现频次与最近出现时间排序");
        sorted.setArgs(Map.of(
            "field", "matchedPhotoCount",
            "direction", "desc",
            "secondaryField", "matchedLastSeen",
            "secondaryDirection", "desc"
        ));
        plan.getSteps().add(sorted);

        AiSearchPlanStep limited = new AiSearchPlanStep();
        limited.setId("limited_temporal_person_set");
        limited.setOperator("limit");
        limited.setInputRef("sorted_temporal_person_set");
        limited.setOutputKey("limited_temporal_person_set");
        limited.getDependsOn().add("sorted_temporal_person_set");
        limited.setDescription("分页截断时序人物集合结果");
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

    private boolean refersToAbsent(String query) {
        return query.contains("不存在") || query.contains("没出现")
            || query.contains("没有出现") || query.contains("未出现");
    }

    private boolean refersToPresent(String query) {
        return query.contains("存在") || query.contains("出现") || query.contains("有");
    }

    private boolean refersToDisappearAgain(String query) {
        return query.contains("没再出现") || query.contains("又没再出现")
            || query.contains("没有再出现") || query.contains("未再出现");
    }
}
