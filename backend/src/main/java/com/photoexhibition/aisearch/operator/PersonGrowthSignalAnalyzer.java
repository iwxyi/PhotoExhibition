package com.photoexhibition.aisearch.operator;

import com.photoexhibition.entity.Face;
import com.photoexhibition.repository.FaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PersonGrowthSignalAnalyzer {

    private final FaceRepository faceRepository;

    public Map<String, Object> analyze(Long personId, String personName, Integer startYear, Integer endYear) {
        if (personId == null || startYear == null || endYear == null) {
            return errorMetrics(personName, startYear, endYear, "缺少人物或时间范围参数");
        }

        try {
            List<Face> faces = faceRepository.findByPersonId(personId);
            List<Face> filteredFaces = faces.stream()
                .filter(f -> f.getPhoto() != null && f.getPhoto().getTakenAt() != null)
                .filter(f -> {
                    int year = f.getPhoto().getTakenAt().getYear();
                    return year >= startYear && year <= endYear;
                })
                .filter(f -> f.getWidth() != null && f.getHeight() != null && f.getWidth() > 0 && f.getHeight() > 0)
                .collect(Collectors.toList());

            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("personId", personId);
            metrics.put("personName", personName);
            metrics.put("startYear", startYear);
            metrics.put("endYear", endYear);
            metrics.put("totalPhotos", filteredFaces.size());

            if (filteredFaces.isEmpty()) {
                metrics.put("trend", "unknown");
                metrics.put("yearlyStats", Collections.emptyList());
                return metrics;
            }

            double totalArea = 0D;
            double totalWidth = 0D;
            double totalHeight = 0D;
            LocalDateTime matchedLastSeen = null;
            for (Face face : filteredFaces) {
                totalArea += face.getWidth() * face.getHeight();
                totalWidth += face.getWidth();
                totalHeight += face.getHeight();
                LocalDateTime takenAt = face.getPhoto().getTakenAt();
                if (takenAt != null && (matchedLastSeen == null || takenAt.isAfter(matchedLastSeen))) {
                    matchedLastSeen = takenAt;
                }
            }
            metrics.put("avgFaceArea", totalArea / filteredFaces.size());
            metrics.put("avgFaceWidth", totalWidth / filteredFaces.size());
            metrics.put("avgFaceHeight", totalHeight / filteredFaces.size());
            metrics.put("avgAspectRatio", totalHeight / totalWidth);
            metrics.put("matchedLastSeen", matchedLastSeen);

            Map<String, List<Face>> byYearMonth = filteredFaces.stream()
                .collect(Collectors.groupingBy(f -> {
                    LocalDateTime takenAt = f.getPhoto().getTakenAt();
                    return takenAt.getYear() + "-" + String.format("%02d", takenAt.getMonthValue());
                }));

            List<Map<String, Object>> yearlyStats = new ArrayList<>();
            for (Map.Entry<String, List<Face>> entry : byYearMonth.entrySet()) {
                String yearMonth = entry.getKey();
                List<Face> monthFaces = entry.getValue();

                double monthArea = monthFaces.stream().mapToDouble(f -> f.getWidth() * f.getHeight()).average().orElse(0D);
                double monthWidth = monthFaces.stream().mapToDouble(Face::getWidth).average().orElse(0D);
                double monthHeight = monthFaces.stream().mapToDouble(Face::getHeight).average().orElse(0D);

                String[] parts = yearMonth.split("-");
                Map<String, Object> monthStats = new LinkedHashMap<>();
                monthStats.put("year", Integer.parseInt(parts[0]));
                monthStats.put("month", Integer.parseInt(parts[1]));
                monthStats.put("label", yearMonth);
                monthStats.put("faceCount", monthFaces.size());
                monthStats.put("avgFaceArea", monthArea);
                monthStats.put("avgFaceWidth", monthWidth);
                monthStats.put("avgFaceHeight", monthHeight);
                monthStats.put("avgAspectRatio", monthHeight / monthWidth);
                yearlyStats.add(monthStats);
            }

            yearlyStats.sort((left, right) -> {
                int leftYear = toInteger(left.get("year"));
                int rightYear = toInteger(right.get("year"));
                int yearCompare = Integer.compare(leftYear, rightYear);
                if (yearCompare != 0) {
                    return yearCompare;
                }
                return Integer.compare(toInteger(left.get("month")), toInteger(right.get("month")));
            });

            metrics.put("yearlyStats", yearlyStats);

            if (yearlyStats.size() >= 2) {
                double firstRatio = toDouble(yearlyStats.get(0).get("avgAspectRatio"));
                double lastRatio = toDouble(yearlyStats.get(yearlyStats.size() - 1).get("avgAspectRatio"));
                String firstPeriod = asString(yearlyStats.get(0).get("label"));
                String lastPeriod = asString(yearlyStats.get(yearlyStats.size() - 1).get("label"));
                double changePercent = ((lastRatio - firstRatio) / firstRatio) * 100D;

                metrics.put("changePercent", changePercent);
                metrics.put("firstPeriod", firstPeriod);
                metrics.put("lastPeriod", lastPeriod);
                metrics.put("firstRatio", firstRatio);
                metrics.put("lastRatio", lastRatio);

                if (changePercent > 2D) {
                    metrics.put("trend", "gained_weight");
                } else if (changePercent < -2D) {
                    metrics.put("trend", "lost_weight");
                } else {
                    metrics.put("trend", "stable");
                }
            } else if (yearlyStats.size() == 1) {
                metrics.put("trend", "single_period");
                metrics.put("firstPeriod", asString(yearlyStats.get(0).get("label")));
                metrics.put("lastPeriod", asString(yearlyStats.get(0).get("label")));
                metrics.put("firstRatio", toDouble(yearlyStats.get(0).get("avgAspectRatio")));
                metrics.put("lastRatio", toDouble(yearlyStats.get(0).get("avgAspectRatio")));
                metrics.put("changePercent", 0D);
            } else {
                metrics.put("trend", "insufficient_data");
            }

            return metrics;
        } catch (Exception e) {
            return errorMetrics(personName, startYear, endYear, e.getMessage());
        }
    }

    private Map<String, Object> errorMetrics(String personName, Integer startYear, Integer endYear, String message) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("personName", personName);
        metrics.put("startYear", startYear);
        metrics.put("endYear", endYear);
        metrics.put("totalPhotos", 0);
        metrics.put("trend", "error");
        metrics.put("errorMessage", message);
        metrics.put("yearlyStats", Collections.emptyList());
        return metrics;
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && !((String) value).isBlank()) {
            return Integer.parseInt((String) value);
        }
        return null;
    }

    private double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0D;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
