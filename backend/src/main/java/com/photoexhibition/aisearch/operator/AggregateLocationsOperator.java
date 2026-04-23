package com.photoexhibition.aisearch.operator;

import com.photoexhibition.aisearch.executor.AiSearchExecutionContext;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import com.photoexhibition.aisearch.support.AlbumLocationCandidateExtractor;
import com.photoexhibition.entity.Album;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.repository.AlbumRepository;
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
public class AggregateLocationsOperator implements AiSearchOperator {

    private final PhotoRepository photoRepository;
    private final AlbumRepository albumRepository;
    private final AlbumLocationCandidateExtractor albumLocationCandidateExtractor;

    @Override
    public String getName() {
        return "aggregate_locations";
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
        Map<Long, List<String>> albumLocations = new LinkedHashMap<>();
        Map<String, Long> counts = new LinkedHashMap<>();

        for (Photo photo : photos) {
            Long albumId = photo.getAlbumId();
            if (albumId == null) {
                continue;
            }
            List<String> locations = albumLocations.get(albumId);
            if (locations == null) {
                locations = albumRepository.findById(albumId)
                    .map(albumLocationCandidateExtractor::extractAlbumLocationCandidates)
                    .orElse(Collections.emptyList());
                albumLocations.put(albumId, locations);
            }
            for (String location : locations) {
                counts.merge(location, 1L, Long::sum);
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
                row.put("location", entry.getKey());
                row.put("photoCount", entry.getValue());
                return row;
            })
            .collect(Collectors.toCollection(ArrayList::new));
    }
}
