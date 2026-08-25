package com.photoexhibition.aisearch.executor;

import com.photoexhibition.aisearch.operator.AiSearchOperator;
import com.photoexhibition.aisearch.operator.AiSearchOperatorRegistry;
import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import com.photoexhibition.aisearch.validation.AiSearchPlanValidator;
import org.springframework.stereotype.Component;

@Component
public class DefaultAiSearchPlanExecutor implements AiSearchPlanExecutor {

    private final AiSearchPlanValidator validator;
    private final AiSearchOperatorRegistry operatorRegistry;

    public DefaultAiSearchPlanExecutor(AiSearchPlanValidator validator, AiSearchOperatorRegistry operatorRegistry) {
        this.validator = validator;
        this.operatorRegistry = operatorRegistry;
    }

    @Override
    public AiSearchExecutionResult execute(AiSearchPlan plan, AiSearchExecutionContext context) {
        validator.validate(plan);

        AiSearchExecutionResult result = new AiSearchExecutionResult();
        for (AiSearchPlanStep step : plan.getSteps()) {
            AiSearchOperator operator = operatorRegistry.find(step.getOperator())
                .orElseThrow(() -> new IllegalArgumentException("未注册的 AI 搜索算子: " + step.getOperator()));
            Object output = operator.execute(step, context);
            context.getValues().put(step.getOutputKey(), output);
            result.getStepOutputs().put(step.getId(), output);
            result.getFinalOutputs().put(step.getOutputKey(), output);
        }
        return result;
    }
}
