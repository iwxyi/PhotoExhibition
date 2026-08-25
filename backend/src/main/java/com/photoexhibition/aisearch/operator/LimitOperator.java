package com.photoexhibition.aisearch.operator;

import com.photoexhibition.aisearch.executor.AiSearchExecutionContext;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class LimitOperator implements AiSearchOperator {

    @Override
    public String getName() {
        return "limit";
    }

    @Override
    public Object execute(AiSearchPlanStep step, AiSearchExecutionContext context) {
        Object input = context.getValues().get(step.getInputRef());
        if (!(input instanceof List)) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<Object> source = new ArrayList<>((List<Object>) input);
        int offset = toInt(step.getArgs().get("offset"));
        int size = Math.max(1, toInt(step.getArgs().get("size")));
        if (offset >= source.size()) {
            return Collections.emptyList();
        }
        return source.subList(offset, Math.min(source.size(), offset + size));
    }

    private int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            return Integer.parseInt((String) value);
        }
        return 0;
    }
}
