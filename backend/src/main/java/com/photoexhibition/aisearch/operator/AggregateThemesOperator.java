package com.photoexhibition.aisearch.operator;

import com.photoexhibition.aisearch.executor.AiSearchExecutionContext;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import com.photoexhibition.aisearch.support.AlbumThemeCandidateExtractor;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.Tag;
import com.photoexhibition.repository.AlbumRepository;
import com.photoexhibition.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AggregateThemesOperator implements AiSearchOperator {

    private final PhotoRepository photoRepository;
    private final AlbumRepository albumRepository;
    private final AlbumThemeCandidateExtractor albumThemeCandidateExtractor;

    @Override
    public String getName() {
        return "aggregate_themes";
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
        Map<Long, List<String>> albumThemes = new HashMap<>();
        Map<String, Integer> albumThemeFrequency = new HashMap<>();
        Map<String, Long> counts = new LinkedHashMap<>();

        for (Photo photo : photos) {
            Set<String> tagThemes = new HashSet<>();
            if (photo.getTags() != null) {
                for (Tag tag : photo.getTags()) {
                    for (String theme : albumThemeCandidateExtractor.extractThemeCandidates(tag == null ? null : tag.getName())) {
                        counts.merge(theme, 1L, Long::sum);
                        tagThemes.add(theme);
                    }
                }
            }

            Long albumId = photo.getAlbumId();
            if (albumId == null) {
                continue;
            }
            List<String> themes = albumThemes.get(albumId);
            if (themes == null || themes.isEmpty()) {
                themes = albumRepository.findById(albumId)
                    .map(albumThemeCandidateExtractor::extractAlbumThemeCandidates)
                    .orElse(Collections.emptyList());
                albumThemes.put(albumId, themes);
                themes.forEach(theme -> albumThemeFrequency.merge(theme, 1, Integer::sum));
            }
            for (String theme : themes) {
                if (!tagThemes.contains(theme) && albumThemeFrequency.getOrDefault(theme, 0) < 2) {
                    continue;
                }
                counts.merge(theme, 2L, Long::sum);
            }
        }

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
                row.put("theme", entry.getKey());
                row.put("photoCount", entry.getValue());
                return row;
            })
            .collect(Collectors.toCollection(ArrayList::new));
    }
}
