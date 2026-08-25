package com.photoexhibition.aisearch.operator;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AiSearchOperatorRegistry {
    private final Map<String, AiSearchOperator> operators;

    public AiSearchOperatorRegistry(List<AiSearchOperator> operators) {
        this.operators = operators.stream()
            .collect(Collectors.toMap(AiSearchOperator::getName, Function.identity(), (left, right) -> left));
    }

    public Optional<AiSearchOperator> find(String name) {
        return Optional.ofNullable(operators.get(name));
    }
}
