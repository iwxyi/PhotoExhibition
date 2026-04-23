package com.photoexhibition.aisearch.operator;

import com.photoexhibition.aisearch.executor.AiSearchExecutionContext;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import com.photoexhibition.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LegacyIntentFilterPhotosOperator implements AiSearchOperator {

    private final PhotoRepository photoRepository;

    @Override
    public String getName() {
        return "filter_photos";
    }

    @Override
    public Object execute(AiSearchPlanStep step, AiSearchExecutionContext context) {
        List<String> cameraModels = stringList(step.getArgs().get("cameraModels"));
        List<String> lensModels = stringList(step.getArgs().get("lensModels"));
        if (!cameraModels.isEmpty() || !lensModels.isEmpty()) {
            LinkedHashSet<Long> ordered = new LinkedHashSet<>();
            for (String cameraModel : cameraModels) {
                ordered.addAll(photoRepository.findVisibleIdsByCameraModelContaining(cameraModel));
            }
            for (String lensModel : lensModels) {
                ordered.addAll(photoRepository.findVisibleIdsByLensModelContaining(lensModel));
            }
            return new ArrayList<>(ordered);
        }

        LocalDateTime startDate = parseDate(step.getArgs().get("startDate"), false);
        LocalDateTime endDate = parseDate(step.getArgs().get("endDate"), true);
        if (startDate != null || endDate != null) {
            List<Long> photoIds = photoRepository.findVisibleIdsByCapturedAtRange(startDate, endDate);
            return photoIds;
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("query", context.getQuery());
        snapshot.put("queryMode", context.getQueryMode());
        snapshot.put("args", step.getArgs());
        return snapshot;
    }

    private LocalDateTime parseDate(Object value, boolean endOfDay) {
        if (!(value instanceof String) || ((String) value).isBlank()) {
            return null;
        }
        String text = (String) value;
        try {
            if (text.length() == 10) {
                return endOfDay
                    ? LocalDateTime.parse(text + "T23:59:59", DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    : LocalDateTime.parse(text + "T00:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List)) {
            return List.of();
        }
        List<?> raw = (List<?>) value;
        List<String> result = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof String && !((String) item).isBlank()) {
                result.add((String) item);
            }
        }
        return result;
    }
}
