package com.photoexhibition.aisearch.operator;

import com.photoexhibition.aisearch.executor.AiSearchExecutionContext;
import com.photoexhibition.aisearch.model.AiSearchPersonAggregate;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class SetIntersectionOperator implements AiSearchOperator {

    @Override
    public String getName() {
        return "set_intersection";
    }

    @Override
    public Object execute(AiSearchPlanStep step, AiSearchExecutionContext context) {
        Object input = context.getValues().get(step.getInputRef());
        Object compareRef = context.getValues().get(String.valueOf(step.getArgs().get("compareWithRef")));
        if (!(input instanceof List) || !(compareRef instanceof List)) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<AiSearchPersonAggregate> source = (List<AiSearchPersonAggregate>) input;
        @SuppressWarnings("unchecked")
        List<AiSearchPersonAggregate> baseline = (List<AiSearchPersonAggregate>) compareRef;

        Set<Long> baselineIds = new HashSet<>();
        for (AiSearchPersonAggregate item : baseline) {
            if (item != null && item.getPersonId() != null) {
                baselineIds.add(item.getPersonId());
            }
        }

        List<AiSearchPersonAggregate> result = new ArrayList<>();
        for (AiSearchPersonAggregate item : source) {
            if (item == null || item.getPersonId() == null || !baselineIds.contains(item.getPersonId())) {
                continue;
            }
            result.add(item);
        }
        return result;
    }
}
