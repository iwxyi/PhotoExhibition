package com.photoexhibition.aisearch.operator;

import com.photoexhibition.aisearch.executor.AiSearchExecutionContext;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.Tag;
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
public class AggregateTagsOperator implements AiSearchOperator {

    private final PhotoRepository photoRepository;

    @Override
    public String getName() {
        return "aggregate_tags";
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
            .flatMap(photo -> photo.getTags() == null ? java.util.stream.Stream.<Tag>empty() : photo.getTags().stream())
            .map(Tag::getName)
            .filter(name -> name != null && !name.isBlank())
            .map(String::trim)
            .collect(Collectors.groupingBy(value -> value, LinkedHashMap::new, Collectors.counting()));

        return counts.entrySet().stream()
            .filter(entry -> entry.getValue() >= 2)
            .sorted((left, right) -> {
                int byCount = Long.compare(right.getValue(), left.getValue());
                if (byCount != 0) {
                    return byCount;
                }
                return left.getKey().compareTo(right.getKey());
            })
            .map(entry -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("tag", entry.getKey());
                row.put("photoCount", entry.getValue());
                return row;
            })
            .collect(Collectors.toCollection(ArrayList::new));
    }
}
