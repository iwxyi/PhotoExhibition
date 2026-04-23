package com.photoexhibition.aisearch.operator;

import com.photoexhibition.aisearch.executor.AiSearchExecutionContext;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AggregateMonthsOperator implements AiSearchOperator {

    private final PhotoRepository photoRepository;

    @Override
    public String getName() {
        return "aggregate_months";
    }

    @Override
    public Object execute(AiSearchPlanStep step, AiSearchExecutionContext context) {
        Object input = context.getValues().get(step.getInputRef());
        if (!(input instanceof List)) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<Long> photoIds = (List<Long>) input;
        if (photoIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Photo> photos = photoRepository.findAllByIdIn(photoIds);
        Map<String, Long> counts = photos.stream()
            .map(Photo::getTakenAt)
            .filter(java.util.Objects::nonNull)
            .map(takenAt -> String.format("%d-%02d", takenAt.getYear(), takenAt.getMonthValue()))
            .collect(Collectors.groupingBy(value -> value, LinkedHashMap::new, Collectors.counting()));

        return counts.entrySet().stream()
            .sorted((left, right) -> {
                int byCount = Long.compare(right.getValue(), left.getValue());
                if (byCount != 0) {
                    return byCount;
                }
                return left.getKey().compareTo(right.getKey());
            })
            .map(entry -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("month", entry.getKey());
                row.put("photoCount", entry.getValue());
                return row;
            })
            .collect(Collectors.toCollection(ArrayList::new));
    }
}
