package com.photoexhibition.aisearch.operator;

import com.photoexhibition.aisearch.executor.AiSearchExecutionContext;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ComparePeriodsOperator implements AiSearchOperator {

    @Override
    public String getName() {
        return "compare_periods";
    }

    @Override
    public Object execute(AiSearchPlanStep step, AiSearchExecutionContext context) {
        long leftCount = sizeOf(context.getValues().get(step.getInputRef()));
        String compareWithRef = String.valueOf(step.getArgs().getOrDefault("compareWithRef", ""));
        long rightCount = sizeOf(context.getValues().get(compareWithRef));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("leftCount", leftCount);
        result.put("rightCount", rightCount);
        result.put("delta", leftCount - rightCount);
        result.put("same", leftCount == rightCount);
        return result;
    }

    private long sizeOf(Object value) {
        if (value instanceof List<?>) {
            return ((List<?>) value).size();
        }
        return 0L;
    }
}
