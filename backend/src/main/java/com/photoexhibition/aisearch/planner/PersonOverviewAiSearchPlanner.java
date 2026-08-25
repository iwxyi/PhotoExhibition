package com.photoexhibition.aisearch.planner;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PersonOverviewAiSearchPlanner {

    public AiSearchPlan plan(String query, int offset, int limit) {
        AiSearchPlan plan = new AiSearchPlan();
        plan.setQuery(query);
        plan.setQueryMode("analysis");
        plan.setPlanType("person_overview");
        plan.setResultTypes(List.of("persons", "photos", "albums"));
        plan.getMetadata().put("intent", "person_overview");

        AiSearchPlanStep aggregatePersons = new AiSearchPlanStep();
        aggregatePersons.setId("aggregate_persons");
        aggregatePersons.setOperator("aggregate_persons");
        aggregatePersons.setInputRef("matched_photo_ids");
        aggregatePersons.setOutputKey("person_aggregates");
        aggregatePersons.setDescription("对已命中的照片集合按人物聚合");
        plan.getSteps().add(aggregatePersons);

        AiSearchPlanStep sortedPersons = new AiSearchPlanStep();
        sortedPersons.setId("sorted_persons");
        sortedPersons.setOperator("sort");
        sortedPersons.setInputRef("person_aggregates");
        sortedPersons.setOutputKey("sorted_persons");
        sortedPersons.getDependsOn().add("aggregate_persons");
        sortedPersons.setDescription("按出现频次与最近出现时间排序人物结果");
        sortedPersons.setArgs(Map.of(
            "field", "matchedPhotoCount",
            "direction", "desc",
            "secondaryField", "matchedLastSeen",
            "secondaryDirection", "desc"
        ));
        plan.getSteps().add(sortedPersons);

        AiSearchPlanStep limitedPersons = new AiSearchPlanStep();
        limitedPersons.setId("limited_persons");
        limitedPersons.setOperator("limit");
        limitedPersons.setInputRef("sorted_persons");
        limitedPersons.setOutputKey("limited_persons");
        limitedPersons.getDependsOn().add("sorted_persons");
        limitedPersons.setDescription("分页截断人物结果");
        limitedPersons.setArgs(Map.of(
            "offset", Math.max(0, offset),
            "size", Math.max(1, limit)
        ));
        plan.getSteps().add(limitedPersons);

        return plan;
    }
}
