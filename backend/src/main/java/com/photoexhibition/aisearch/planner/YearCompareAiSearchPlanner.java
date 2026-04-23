package com.photoexhibition.aisearch.planner;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class YearCompareAiSearchPlanner {

    public AiSearchPlan plan(String query, int leftYear, int rightYear, String subject) {
        AiSearchPlan plan = new AiSearchPlan();
        plan.setQuery(query);
        plan.setQueryMode("analysis");
        plan.setPlanType("year_compare");
        plan.setResultTypes(List.of("photos", "albums"));
        plan.getMetadata().put("intent", "year_compare");
        plan.getMetadata().put("leftYear", leftYear);
        plan.getMetadata().put("rightYear", rightYear);
        plan.getMetadata().put("subject", subject);

        AiSearchPlanStep comparePeriods = new AiSearchPlanStep();
        comparePeriods.setId("compare_periods");
        comparePeriods.setOperator("compare_periods");
        comparePeriods.setInputRef("left_period_photo_ids");
        comparePeriods.setOutputKey("period_comparison");
        comparePeriods.setDescription("对左右两个时期的命中照片集合做数量对比");
        comparePeriods.setArgs(Map.of("compareWithRef", "right_period_photo_ids"));
        plan.getSteps().add(comparePeriods);

        return plan;
    }
}
