package com.photoexhibition.aisearch.operator;

import com.photoexhibition.aisearch.executor.AiSearchExecutionContext;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import com.photoexhibition.entity.Album;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.repository.AlbumRepository;
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

    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;

    @Override
    public String getName() {
        return "filter_photos";
    }

    @Override
    public Object execute(AiSearchPlanStep step, AiSearchExecutionContext context) {
        List<String> cameraModels = stringList(step.getArgs().get("cameraModels"));
        List<String> lensModels = stringList(step.getArgs().get("lensModels"));
        List<String> keywords = stringList(step.getArgs().get("keywords"));
        LocalDateTime startDate = parseDate(step.getArgs().get("startDate"), false);
        LocalDateTime endDate = parseDate(step.getArgs().get("endDate"), true);

        LinkedHashSet<Long> technicalMatches = null;
        if (!cameraModels.isEmpty() || !lensModels.isEmpty()) {
            technicalMatches = new LinkedHashSet<>();
            for (String cameraModel : cameraModels) {
                technicalMatches.addAll(photoRepository.findVisibleIdsByCameraModelContaining(cameraModel));
            }
            for (String lensModel : lensModels) {
                technicalMatches.addAll(photoRepository.findVisibleIdsByLensModelContaining(lensModel));
            }
        }

        LinkedHashSet<Long> dateMatches = null;
        if (startDate != null || endDate != null) {
            dateMatches = new LinkedHashSet<>(photoRepository.findVisibleIdsByCapturedAtRange(startDate, endDate));
        }

        LinkedHashSet<Long> keywordMatches = null;
        if (!keywords.isEmpty()) {
            keywordMatches = new LinkedHashSet<>();
            for (String keyword : keywords) {
                for (Photo photo : photoRepository.searchByFilename(keyword)) {
                    if (photo != null && photo.getId() != null) {
                        keywordMatches.add(photo.getId());
                    }
                }

                LinkedHashSet<Long> albumIds = new LinkedHashSet<>();
                for (Album album : albumRepository.searchByName(keyword)) {
                    if (album != null && album.getId() != null && !Boolean.TRUE.equals(album.getIsHidden())) {
                        albumIds.add(album.getId());
                    }
                }
                for (Album album : albumRepository.searchByPath(keyword)) {
                    if (album != null && album.getId() != null && !Boolean.TRUE.equals(album.getIsHidden())) {
                        albumIds.add(album.getId());
                    }
                }
                if (!albumIds.isEmpty()) {
                    keywordMatches.addAll(photoRepository.findVisibleIdsByAlbumIds(new ArrayList<>(albumIds)));
                }
            }
        }

        LinkedHashSet<Long> result = combine(null, technicalMatches);
        result = combine(result, dateMatches);
        result = combine(result, keywordMatches);
        if (result != null) {
            return new ArrayList<>(result);
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("query", context.getQuery());
        snapshot.put("queryMode", context.getQueryMode());
        snapshot.put("args", step.getArgs());
        return snapshot;
    }

    private LinkedHashSet<Long> combine(LinkedHashSet<Long> current, LinkedHashSet<Long> incoming) {
        if (incoming == null) {
            return current;
        }
        if (current == null) {
            return new LinkedHashSet<>(incoming);
        }
        current.retainAll(incoming);
        return current;
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
