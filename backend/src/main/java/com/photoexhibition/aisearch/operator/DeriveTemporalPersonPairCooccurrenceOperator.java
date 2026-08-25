package com.photoexhibition.aisearch.operator;

import com.photoexhibition.aisearch.executor.AiSearchExecutionContext;
import com.photoexhibition.aisearch.model.AiSearchPersonAggregate;
import com.photoexhibition.aisearch.model.AiSearchPersonPairAggregate;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import com.photoexhibition.repository.FaceRepository;
import com.photoexhibition.repository.PersonProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DeriveTemporalPersonPairCooccurrenceOperator implements AiSearchOperator {

    private final FaceRepository faceRepository;
    private final PersonProfileRepository personProfileRepository;

    @Override
    public String getName() {
        return "derive_temporal_person_pair_cooccurrence";
    }

    @Override
    public Object execute(AiSearchPlanStep step, AiSearchExecutionContext context) {
        Object input = context.getValues().get(step.getInputRef());
        if (!(input instanceof List)) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<AiSearchPersonAggregate> candidates = (List<AiSearchPersonAggregate>) input;
        List<Long> candidatePersonIds = candidates.stream()
            .map(AiSearchPersonAggregate::getPersonId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        if (candidatePersonIds.size() < 2) {
            return Collections.emptyList();
        }

        List<AiSearchPersonPairAggregate> aggregates = new ArrayList<>();
        for (Object[] row : faceRepository.summarizePersonPairCooccurrencesAfterFirstSeen(candidatePersonIds)) {
            Long personAId = toLong(row[0]);
            Long personBId = toLong(row[1]);
            if (personAId == null || personBId == null) {
                continue;
            }

            AiSearchPersonPairAggregate aggregate = new AiSearchPersonPairAggregate();
            aggregate.setPersonAId(personAId);
            aggregate.setPersonBId(personBId);
            aggregate.setMatchedPhotoCount(toInt(row[2]));
            aggregate.setMatchedFirstSeen(toDateTime(row[3]));
            aggregate.setMatchedLastSeen(toDateTime(row[4]));
            aggregate.setPersonAName(personProfileRepository.findById(personAId).map(person -> person.getName()).orElse(""));
            aggregate.setPersonBName(personProfileRepository.findById(personBId).map(person -> person.getName()).orElse(""));
            if (!aggregate.getPersonAName().isBlank() && !aggregate.getPersonBName().isBlank()) {
                aggregates.add(aggregate);
            }
        }
        return aggregates;
    }

    private Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ignored) {
                return null;
            }
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
