package com.photoexhibition.aisearch.operator;

import com.photoexhibition.aisearch.executor.AiSearchExecutionContext;
import com.photoexhibition.aisearch.model.AiSearchPersonAggregate;
import com.photoexhibition.aisearch.model.AiSearchPersonGrowthAggregate;
import com.photoexhibition.aisearch.model.AiSearchPersonPairAggregate;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Component
public class SortOperator implements AiSearchOperator {

    @Override
    public String getName() {
        return "sort";
    }

    @Override
    public Object execute(AiSearchPlanStep step, AiSearchExecutionContext context) {
        Object input = context.getValues().get(step.getInputRef());
        if (!(input instanceof List)) {
            return Collections.emptyList();
        }

        List<Object> source = new ArrayList<>((List<?>) input);
        Comparator<Object> comparator = buildComparator(
            String.valueOf(step.getArgs().get("field")),
            String.valueOf(step.getArgs().getOrDefault("direction", "desc"))
        );
        Object secondaryField = step.getArgs().get("secondaryField");
        if (secondaryField != null) {
            comparator = comparator.thenComparing(buildComparator(
                String.valueOf(secondaryField),
                String.valueOf(step.getArgs().getOrDefault("secondaryDirection", "desc"))
            ));
        }
        source.sort(comparator);
        return source;
    }

    private Comparator<Object> buildComparator(String field, String direction) {
        Comparator<Object> comparator;
        switch (field) {
            case "matchedLastSeen":
                comparator = Comparator.comparing(item -> nullSafeDate(extractDate(item, field)));
                break;
            case "globalFirstSeen":
                comparator = Comparator.comparing(item -> nullSafeDate(extractDate(item, field)));
                break;
            case "changePercent":
                comparator = Comparator.comparing(item -> extractDouble(item, field));
                break;
            case "matchedPhotoCount":
            default:
                comparator = Comparator.comparing(item -> extractInt(item, field));
                break;
        }
        return "asc".equalsIgnoreCase(direction) ? comparator : comparator.reversed();
    }

    private Integer extractInt(Object item, String field) {
        if (item instanceof AiSearchPersonAggregate) {
            AiSearchPersonAggregate aggregate = (AiSearchPersonAggregate) item;
            if ("matchedPhotoCount".equals(field)) {
                return aggregate.getMatchedPhotoCount() == null ? 0 : aggregate.getMatchedPhotoCount();
            }
        }
        if (item instanceof AiSearchPersonPairAggregate) {
            AiSearchPersonPairAggregate aggregate = (AiSearchPersonPairAggregate) item;
            if ("matchedPhotoCount".equals(field)) {
                return aggregate.getMatchedPhotoCount() == null ? 0 : aggregate.getMatchedPhotoCount();
            }
        }
        if (item instanceof AiSearchPersonGrowthAggregate) {
            AiSearchPersonGrowthAggregate aggregate = (AiSearchPersonGrowthAggregate) item;
            if ("matchedPhotoCount".equals(field)) {
                return aggregate.getMatchedPhotoCount() == null ? 0 : aggregate.getMatchedPhotoCount();
            }
        }
        return 0;
    }

    private Double extractDouble(Object item, String field) {
        if (item instanceof AiSearchPersonGrowthAggregate) {
            AiSearchPersonGrowthAggregate aggregate = (AiSearchPersonGrowthAggregate) item;
            if ("changePercent".equals(field)) {
                return aggregate.getChangePercent() == null ? 0D : aggregate.getChangePercent();
            }
        }
        return 0D;
    }

    private LocalDateTime extractDate(Object item, String field) {
        if (item instanceof AiSearchPersonAggregate) {
            AiSearchPersonAggregate aggregate = (AiSearchPersonAggregate) item;
            if ("matchedLastSeen".equals(field)) {
                return aggregate.getMatchedLastSeen();
            }
            if ("globalFirstSeen".equals(field)) {
                return aggregate.getGlobalFirstSeen();
            }
        }
        if (item instanceof AiSearchPersonPairAggregate) {
            AiSearchPersonPairAggregate aggregate = (AiSearchPersonPairAggregate) item;
            if ("matchedLastSeen".equals(field) || "globalFirstSeen".equals(field)) {
                return aggregate.getMatchedLastSeen();
            }
        }
        if (item instanceof AiSearchPersonGrowthAggregate) {
            AiSearchPersonGrowthAggregate aggregate = (AiSearchPersonGrowthAggregate) item;
            if ("matchedLastSeen".equals(field) || "globalFirstSeen".equals(field)) {
                return aggregate.getMatchedLastSeen();
            }
        }
        return null;
    }

    private LocalDateTime nullSafeDate(LocalDateTime value) {
        return value == null ? LocalDateTime.MIN : value;
    }
}
