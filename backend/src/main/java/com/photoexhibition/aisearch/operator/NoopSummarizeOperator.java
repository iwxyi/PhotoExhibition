package com.photoexhibition.aisearch.operator;

import com.photoexhibition.aisearch.executor.AiSearchExecutionContext;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class NoopSummarizeOperator implements AiSearchOperator {

    @Override
    public String getName() {
        return "summarize";
    }

    @Override
    public Object execute(AiSearchPlanStep step, AiSearchExecutionContext context) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("inputRef", step.getInputRef());
        summary.put("args", step.getArgs());
        summary.put("availableKeys", context.getValues().keySet());
        return summary;
    }
}
