package com.photoexhibition.aisearch.planner;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PersonPairCooccurrenceAiSearchPlanner {

    public AiSearchPlan plan(String query, int offset, int limit) {
        AiSearchPlan plan = new AiSearchPlan();
        plan.setQuery(query);
        plan.setQueryMode("analysis");
        plan.setPlanType("person_pair_cooccurrence");
        plan.setResultTypes(List.of("photos", "albums"));
        plan.getMetadata().put("intent", "person_pair_cooccurrence");

        AiSearchPlanStep derivePairs = new AiSearchPlanStep();
        derivePairs.setId("derive_person_pair_cooccurrence");
        derivePairs.setOperator("derive_person_pair_cooccurrence");
        derivePairs.setInputRef("matched_photo_ids");
        derivePairs.setOutputKey("cooccurring_pairs");
        derivePairs.setDescription("在已命中的照片集合中推导共同出现频率较高的人物对");
        plan.getSteps().add(derivePairs);

        AiSearchPlanStep sort = new AiSearchPlanStep();
        sort.setId("sorted_cooccurring_pairs");
        sort.setOperator("sort");
        sort.setInputRef("cooccurring_pairs");
        sort.setOutputKey("sorted_cooccurring_pairs");
        sort.getDependsOn().add("derive_person_pair_cooccurrence");
        sort.setDescription("按共同出现频次与最近出现时间排序人物对");
        sort.setArgs(Map.of(
            "field", "matchedPhotoCount",
            "direction", "desc",
            "secondaryField", "matchedLastSeen",
            "secondaryDirection", "desc"
        ));
        plan.getSteps().add(sort);

        AiSearchPlanStep limitStep = new AiSearchPlanStep();
        limitStep.setId("limited_cooccurring_pairs");
        limitStep.setOperator("limit");
        limitStep.setInputRef("sorted_cooccurring_pairs");
        limitStep.setOutputKey("limited_cooccurring_pairs");
        limitStep.getDependsOn().add("sorted_cooccurring_pairs");
        limitStep.setDescription("分页截断共同出现人物对结果");
        limitStep.setArgs(Map.of(
            "offset", Math.max(0, offset),
            "size", Math.max(1, limit)
        ));
        plan.getSteps().add(limitStep);

        return plan;
    }
}
