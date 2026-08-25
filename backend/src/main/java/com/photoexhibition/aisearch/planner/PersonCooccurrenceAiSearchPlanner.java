package com.photoexhibition.aisearch.planner;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PersonCooccurrenceAiSearchPlanner {

    public AiSearchPlan plan(String query, Long anchorPersonId, int offset, int limit) {
        AiSearchPlan plan = new AiSearchPlan();
        plan.setQuery(query);
        plan.setQueryMode("analysis");
        plan.setPlanType("person_cooccurrence");
        plan.setResultTypes(List.of("persons", "photos", "albums"));
        plan.getMetadata().put("intent", "person_cooccurrence");
        plan.getMetadata().put("anchorPersonId", anchorPersonId);

        AiSearchPlanStep deriveCooccurrence = new AiSearchPlanStep();
        deriveCooccurrence.setId("derive_person_cooccurrence");
        deriveCooccurrence.setOperator("derive_person_cooccurrence");
        deriveCooccurrence.setInputRef("matched_photo_ids");
        deriveCooccurrence.setOutputKey("cooccurring_persons");
        deriveCooccurrence.setDescription("在已命中的照片集合中推导与锚点人物共同出现的人物");
        deriveCooccurrence.setArgs(Map.of("anchorPersonId", anchorPersonId));
        plan.getSteps().add(deriveCooccurrence);

        AiSearchPlanStep sort = new AiSearchPlanStep();
        sort.setId("sorted_cooccurring_persons");
        sort.setOperator("sort");
        sort.setInputRef("cooccurring_persons");
        sort.setOutputKey("sorted_cooccurring_persons");
        sort.getDependsOn().add("derive_person_cooccurrence");
        sort.setDescription("按同框频次与最近同框时间排序");
        sort.setArgs(Map.of(
            "field", "matchedPhotoCount",
            "direction", "desc",
            "secondaryField", "matchedLastSeen",
            "secondaryDirection", "desc"
        ));
        plan.getSteps().add(sort);

        AiSearchPlanStep limitStep = new AiSearchPlanStep();
        limitStep.setId("limited_cooccurring_persons");
        limitStep.setOperator("limit");
        limitStep.setInputRef("sorted_cooccurring_persons");
        limitStep.setOutputKey("limited_cooccurring_persons");
        limitStep.getDependsOn().add("sorted_cooccurring_persons");
        limitStep.setDescription("分页截断共同出现人物结果");
        limitStep.setArgs(Map.of(
            "offset", Math.max(0, offset),
            "size", Math.max(1, limit)
        ));
        plan.getSteps().add(limitStep);

        return plan;
    }
}
