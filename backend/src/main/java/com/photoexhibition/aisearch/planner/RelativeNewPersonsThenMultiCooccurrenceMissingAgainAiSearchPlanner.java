package com.photoexhibition.aisearch.planner;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class RelativeNewPersonsThenMultiCooccurrenceMissingAgainAiSearchPlanner {

    public boolean supports(String query, int anchorCount) {
        if (query == null || query.isBlank()) {
            return false;
        }
        return anchorCount >= 2
            && refersToPersons(query)
            && refersToNewness(query)
            && query.contains("去年")
            && query.contains("今年")
            && refersToLater(query)
            && refersToCooccurrence(query)
            && refersToDisappearAgain(query);
    }

    public AiSearchPlan plan(String query, List<Long> anchorPersonIds, List<String> anchorPersonNames, int offset, int limit) {
        int currentYear = LocalDate.now().getYear();
        int targetYear = currentYear - 1;
        return planForYears(query, targetYear, currentYear, anchorPersonIds, anchorPersonNames, offset, limit);
    }

    public AiSearchPlan planForYears(String query,
                                     int targetYear,
                                     int missingAgainYear,
                                     List<Long> anchorPersonIds,
                                     List<String> anchorPersonNames,
                                     int offset,
                                     int limit) {
        AiSearchPlan plan = new AiSearchPlan();
        plan.setQuery(query);
        plan.setQueryMode("analysis");
        plan.setPlanType("relative_new_persons_then_multi_cooccurrence_missing_again");
        plan.setResultTypes(List.of("persons"));
        plan.getMetadata().put("targetYear", targetYear);
        plan.getMetadata().put("missingAgainYear", missingAgainYear);
        plan.getMetadata().put("anchorPersonIds", new ArrayList<>(anchorPersonIds));
        plan.getMetadata().put("anchorPersonNames", new ArrayList<>(anchorPersonNames));
        plan.getMetadata().put("intent", "relative_new_persons_then_multi_cooccurrence_missing_again");

        plan.getSteps().add(yearFilterStep("target_year_photos", "筛选去年中的可见照片", targetYear));
        plan.getSteps().add(endBoundFilterStep("baseline_photos", "筛选去年之前的可见照片", targetYear - 1));
        plan.getSteps().add(yearFilterStep("missing_again_year_photos", "筛选再次缺席判断年份中的可见照片", missingAgainYear));

        plan.getSteps().add(aggregatePersonsStep("target_year_persons", "target_year_photos", "聚合去年出现的人物"));
        plan.getSteps().add(aggregatePersonsStep("baseline_persons", "baseline_photos", "聚合去年之前出现的人物"));
        plan.getSteps().add(aggregatePersonsStep("missing_again_year_persons", "missing_again_year_photos", "聚合今年出现的人物"));

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
        derive.setId("new_persons_multi_cooccurrence");
        derive.setOperator("derive_temporal_person_cooccurrence");
        derive.setInputRef("new_persons");
        derive.setOutputKey("new_persons_multi_cooccurrence");
        derive.getDependsOn().add("new_persons");
        derive.setDescription("统计新认识人物在首次出现后与多位锚点人物共同出现的频率");
        derive.setArgs(Map.of("anchorPersonIds", new ArrayList<>(anchorPersonIds)));
        plan.getSteps().add(derive);

        AiSearchPlanStep missingAgain = new AiSearchPlanStep();
        missingAgain.setId("multi_cooccurring_new_persons_missing_again");
        missingAgain.setOperator("set_difference");
        missingAgain.setInputRef("new_persons_multi_cooccurrence");
        missingAgain.setOutputKey("multi_cooccurring_new_persons_missing_again");
        missingAgain.getDependsOn().add("new_persons_multi_cooccurrence");
        missingAgain.getDependsOn().add("missing_again_year_persons");
        missingAgain.setDescription("剔除今年又出现的人物，保留后来与锚点组同框但今年没再出现的人");
        missingAgain.setArgs(Map.of("compareWithRef", "missing_again_year_persons"));
        plan.getSteps().add(missingAgain);

        AiSearchPlanStep sorted = new AiSearchPlanStep();
        sorted.setId("sorted_multi_cooccurring_new_persons_missing_again");
        sorted.setOperator("sort");
        sorted.setInputRef("multi_cooccurring_new_persons_missing_again");
        sorted.setOutputKey("sorted_multi_cooccurring_new_persons_missing_again");
        sorted.getDependsOn().add("multi_cooccurring_new_persons_missing_again");
        sorted.setDescription("按后续多锚点同框频次与最近同框时间排序");
        sorted.setArgs(Map.of(
            "field", "matchedPhotoCount",
            "direction", "desc",
            "secondaryField", "matchedLastSeen",
            "secondaryDirection", "desc"
        ));
        plan.getSteps().add(sorted);

        AiSearchPlanStep limited = new AiSearchPlanStep();
        limited.setId("limited_multi_cooccurring_new_persons_missing_again");
        limited.setOperator("limit");
        limited.setInputRef("sorted_multi_cooccurring_new_persons_missing_again");
        limited.setOutputKey("limited_multi_cooccurring_new_persons_missing_again");
        limited.getDependsOn().add("sorted_multi_cooccurring_new_persons_missing_again");
        limited.setDescription("分页截断多锚点后续同框但今年没再出现的人物结果");
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
            || query.contains("哪些人物") || query.contains("人物")
            || query.contains("的人") || query.contains("人里");
    }

    private boolean refersToNewness(String query) {
        return query.contains("新认识") || query.contains("新出现")
            || query.contains("第一次出现") || query.contains("首次出现") || query.contains("才出现");
    }

    private boolean refersToLater(String query) {
        return query.contains("后来") || query.contains("之后") || query.contains("后续");
    }

    private boolean refersToCooccurrence(String query) {
        return query.contains("同框") || query.contains("一起出现")
            || query.contains("经常一起") || query.contains("共同出现");
    }

    private boolean refersToDisappearAgain(String query) {
        return query.contains("没再出现") || query.contains("又没再出现")
            || query.contains("没有再出现") || query.contains("未再出现");
    }
}
