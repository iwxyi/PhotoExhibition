package com.photoexhibition.aisearch.operator;

import com.photoexhibition.aisearch.executor.AiSearchExecutionContext;
import com.photoexhibition.aisearch.model.AiSearchPersonAggregate;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import com.photoexhibition.repository.FaceRepository;
import com.photoexhibition.repository.PersonProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AggregatePersonsOperator implements AiSearchOperator {

    private final FaceRepository faceRepository;
    private final PersonProfileRepository personProfileRepository;

    @Override
    public String getName() {
        return "aggregate_persons";
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

        Map<Long, AiSearchPersonAggregate> aggregates = new LinkedHashMap<>();
        for (Object[] row : faceRepository.summarizePersonAppearancesByPhotoIds(photoIds)) {
            Long personId = toLong(row[0]);
            if (personId == null) {
                continue;
            }
            AiSearchPersonAggregate aggregate = new AiSearchPersonAggregate();
            aggregate.setPersonId(personId);
            aggregate.setMatchedPhotoCount(toInt(row[1]));
            aggregate.setMatchedFirstSeen(toDateTime(row[2]));
            aggregate.setMatchedLastSeen(toDateTime(row[3]));
            personProfileRepository.findById(personId).ifPresent(person -> aggregate.setPersonName(person.getName()));
            aggregates.put(personId, aggregate);
        }

        if (aggregates.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> personIds = new ArrayList<>(aggregates.keySet());
        for (Object[] row : faceRepository.summarizeGlobalPersonAppearances(personIds)) {
            Long personId = toLong(row[0]);
            AiSearchPersonAggregate aggregate = aggregates.get(personId);
            if (aggregate == null) {
                continue;
            }
            aggregate.setGlobalFirstSeen(toDateTime(row[1]));
            aggregate.setGlobalLastSeen(toDateTime(row[2]));
        }

        return new ArrayList<>(aggregates.values());
    }

    private Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    private Integer toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private LocalDateTime toDateTime(Object value) {
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime();
        }
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        return null;
    }
}
