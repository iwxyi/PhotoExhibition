package com.photoexhibition.aisearch.orchestration;

import com.photoexhibition.aisearch.model.AiSearchPersonAggregate;
import com.photoexhibition.aisearch.model.AiSearchPersonPairAggregate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AiSearchPersonAnalysisSupport {

    public Map<String, Object> buildOverviewMetrics(String periodLabel,
                                                    List<AiSearchPersonAggregate> persons) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("periodLabel", periodLabel);
        metrics.put("totalMatched", safeList(persons).size());
        metrics.put("summaryItems", safeList(persons).stream()
            .limit(5)
            .map(item -> displayPersonName(item.getPersonName(), item.getPersonId())
                + "(" + safeCount(item.getMatchedPhotoCount()) + "张)")
            .collect(Collectors.toList()));
        metrics.put("topPersons", safeList(persons).stream()
            .limit(5)
            .map(item -> Map.of(
                "personId", item.getPersonId(),
                "personName", displayPersonName(item.getPersonName(), item.getPersonId()),
                "matchedPhotoCount", safeCount(item.getMatchedPhotoCount())
            ))
            .collect(Collectors.toList()));
        return metrics;
    }

    public Map<String, Object> buildCooccurrenceMetrics(String periodLabel,
                                                        String anchorPersonName,
                                                        long photoMatched,
                                                        List<AiSearchPersonAggregate> persons) {
        Map<String, Object> metrics = buildOverviewMetrics(periodLabel, persons);
        metrics.put("anchorPersonName", anchorPersonName);
        metrics.put("photoMatched", photoMatched);
        return metrics;
    }

    public Map<String, Object> buildPairCooccurrenceMetrics(String periodLabel,
                                                            long photoMatched,
                                                            List<AiSearchPersonPairAggregate> pairs) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("periodLabel", periodLabel);
        metrics.put("totalMatched", safeList(pairs).size());
        metrics.put("summaryItems", safeList(pairs).stream()
            .limit(5)
            .map(item -> item.getPersonAName() + " / " + item.getPersonBName()
                + "(" + safeCount(item.getMatchedPhotoCount()) + "张)")
            .collect(Collectors.toList()));
        metrics.put("photoMatched", photoMatched);
        metrics.put("topPairs", safeList(pairs).stream()
            .limit(5)
            .map(item -> Map.of(
                "personAId", item.getPersonAId(),
                "personAName", item.getPersonAName(),
                "personBId", item.getPersonBId(),
                "personBName", item.getPersonBName(),
                "matchedPhotoCount", safeCount(item.getMatchedPhotoCount())
            ))
            .collect(Collectors.toList()));
        return metrics;
    }

    public Map<String, Object> buildOverviewAnalysisData(Map<String, Object> metrics,
                                                         String answer) {
        return buildAnalysisData(metrics, "person_overview", answer, Map.of("topPersons", "topPersons"));
    }

    public Map<String, Object> buildCooccurrenceAnalysisData(Map<String, Object> metrics,
                                                             String answer) {
        return buildAnalysisData(
            metrics,
            "person_cooccurrence",
            answer,
            Map.of(
                "anchorPersonName", "anchorPersonName",
                "photoMatched", "photoMatched",
                "topPersons", "topPersons"
            )
        );
    }

    public Map<String, Object> buildPairCooccurrenceAnalysisData(Map<String, Object> metrics,
                                                                 String answer) {
        return buildAnalysisData(
            metrics,
            "person_pair_cooccurrence",
            answer,
            Map.of(
                "photoMatched", "photoMatched",
                "topPairs", "topPairs"
            )
        );
    }

    private Map<String, Object> buildAnalysisData(Map<String, Object> metrics,
                                                  String analysisType,
                                                  String answer,
                                                  Map<String, String> extraMetricMappings) {
        Map<String, Object> safeMetrics = metrics == null ? Collections.emptyMap() : metrics;
        Map<String, Object> analysisData = new LinkedHashMap<>();
        analysisData.put("analysisType", analysisType);
        analysisData.put("periodLabel", safeMetrics.get("periodLabel"));
        analysisData.put("totalEntities", safeMetrics.get("totalMatched"));
        analysisData.put("conclusion", answer);
        analysisData.put("summaryItems", safeMetrics.getOrDefault("summaryItems", Collections.emptyList()));
        for (Map.Entry<String, String> entry : extraMetricMappings.entrySet()) {
            analysisData.put(entry.getKey(), safeMetrics.getOrDefault(entry.getValue(), Collections.emptyList()));
        }
        return analysisData;
    }

    private String displayPersonName(String personName, Long personId) {
        return personName == null || personName.isBlank() ? "人物#" + personId : personName;
    }

    private long safeCount(Number count) {
        return count == null ? 0L : count.longValue();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }
}
