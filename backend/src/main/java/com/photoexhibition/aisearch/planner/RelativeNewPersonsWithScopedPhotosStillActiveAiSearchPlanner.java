package com.photoexhibition.aisearch.planner;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class RelativeNewPersonsWithScopedPhotosStillActiveAiSearchPlanner {

    private final RelativeNewPersonsWithScopedPhotosAiSearchPlanner scopedPhotosPlanner;

    public RelativeNewPersonsWithScopedPhotosStillActiveAiSearchPlanner(RelativeNewPersonsWithScopedPhotosAiSearchPlanner scopedPhotosPlanner) {
        this.scopedPhotosPlanner = scopedPhotosPlanner;
    }

    public boolean supports(String query, List<String> cameraCandidates, List<String> lensCandidates) {
        if (query == null || query.isBlank()) {
            return false;
        }
        return query.contains("去年")
            && query.contains("今年")
            && refersToStillActive(query)
            && scopedPhotosPlanner.supports(query, cameraCandidates, lensCandidates);
    }

    public AiSearchPlan plan(String query, List<String> cameraCandidates, List<String> lensCandidates, int offset, int limit) {
        AiSearchPlan basePlan = scopedPhotosPlanner.plan(query, cameraCandidates, lensCandidates, offset, limit);
        int currentYear = LocalDate.now().getYear();
        int targetYear = currentYear - 1;
        return planForScope(query, targetYear, currentYear, basePlan, offset, limit);
    }

    public AiSearchPlan planForScope(String query,
                                     int targetYear,
                                     int activeYear,
                                     List<String> cameraModels,
                                     List<String> lensModels,
                                     List<String> scopeKeywords,
                                     int offset,
                                     int limit) {
        AiSearchPlan basePlan = scopedPhotosPlanner.planForScope(query, targetYear, cameraModels, lensModels, scopeKeywords, offset, limit);
        return planForScope(query, targetYear, activeYear, basePlan, offset, limit);
    }

    private AiSearchPlan planForScope(String query,
                                      int targetYear,
                                      int activeYear,
                                      AiSearchPlan basePlan,
                                      int offset,
                                      int limit) {
        AiSearchPlan plan = new AiSearchPlan();
        plan.setQuery(query);
        plan.setQueryMode("analysis");
        plan.setPlanType("relative_new_persons_with_scoped_photos_still_active");
        plan.setResultTypes(List.of("persons"));
        plan.getMetadata().putAll(basePlan.getMetadata());
        plan.getMetadata().put("activeYear", activeYear);
        plan.getMetadata().put("intent", "relative_new_persons_with_scoped_photos_still_active");

        plan.getSteps().addAll(basePlan.getSteps().subList(0, 8));

        AiSearchPlanStep activeYearPhotos = new AiSearchPlanStep();
        activeYearPhotos.setId("active_year_photos");
        activeYearPhotos.setOperator("filter_photos");
        activeYearPhotos.setOutputKey("active_year_photos");
        activeYearPhotos.setDescription("筛选活跃年份中的可见照片");
        activeYearPhotos.setArgs(Map.of(
            "startDate", activeYear + "-01-01",
            "endDate", activeYear + "-12-31"
        ));
        plan.getSteps().add(activeYearPhotos);

        AiSearchPlanStep activeYearPersons = new AiSearchPlanStep();
        activeYearPersons.setId("active_year_persons");
        activeYearPersons.setOperator("aggregate_persons");
        activeYearPersons.setInputRef("active_year_photos");
        activeYearPersons.setOutputKey("active_year_persons");
        activeYearPersons.getDependsOn().add("active_year_photos");
        activeYearPersons.setDescription("聚合今年出现的人物");
        plan.getSteps().add(activeYearPersons);

        AiSearchPlanStep stillActiveScopedNewPersons = new AiSearchPlanStep();
        stillActiveScopedNewPersons.setId("still_active_scoped_new_persons");
        stillActiveScopedNewPersons.setOperator("set_intersection");
        stillActiveScopedNewPersons.setInputRef("active_year_persons");
        stillActiveScopedNewPersons.setOutputKey("still_active_scoped_new_persons");
        stillActiveScopedNewPersons.getDependsOn().add("active_year_persons");
        stillActiveScopedNewPersons.getDependsOn().add("scoped_new_persons");
        stillActiveScopedNewPersons.setDescription("保留范围内新认识且今年仍有出现的人物");
        stillActiveScopedNewPersons.setArgs(Map.of("compareWithRef", "scoped_new_persons"));
        plan.getSteps().add(stillActiveScopedNewPersons);

        AiSearchPlanStep sorted = new AiSearchPlanStep();
        sorted.setId("sorted_still_active_scoped_new_persons");
        sorted.setOperator("sort");
        sorted.setInputRef("still_active_scoped_new_persons");
        sorted.setOutputKey("sorted_still_active_scoped_new_persons");
        sorted.getDependsOn().add("still_active_scoped_new_persons");
        sorted.setDescription("按今年出现频次与最近出现时间排序");
        sorted.setArgs(Map.of(
            "field", "matchedPhotoCount",
            "direction", "desc",
            "secondaryField", "matchedLastSeen",
            "secondaryDirection", "desc"
        ));
        plan.getSteps().add(sorted);

        AiSearchPlanStep limited = new AiSearchPlanStep();
        limited.setId("limited_still_active_scoped_new_persons");
        limited.setOperator("limit");
        limited.setInputRef("sorted_still_active_scoped_new_persons");
        limited.setOutputKey("limited_still_active_scoped_new_persons");
        limited.getDependsOn().add("sorted_still_active_scoped_new_persons");
        limited.setDescription("分页截断范围内且今年仍活跃的新认识人物结果");
        limited.setArgs(Map.of(
            "offset", Math.max(0, offset),
            "size", Math.max(1, limit)
        ));
        plan.getSteps().add(limited);

        return plan;
    }

    private boolean refersToStillActive(String query) {
        return query.contains("还经常出现") || query.contains("还常出现")
            || query.contains("还出现") || query.contains("仍然出现") || query.contains("今年还有");
    }
}
