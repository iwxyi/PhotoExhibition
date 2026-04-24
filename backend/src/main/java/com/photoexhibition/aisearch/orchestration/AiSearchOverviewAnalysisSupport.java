package com.photoexhibition.aisearch.orchestration;

import com.photoexhibition.dto.AiSearchIntent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiSearchOverviewAnalysisSupport {

    public AiSearchIntent createScopedAnalysisIntent(List<String> resultTypes,
                                                     String answerPrompt,
                                                     String explanation) {
        AiSearchIntent intent = new AiSearchIntent();
        intent.setPersonIds(new ArrayList<>());
        intent.setTagIds(new ArrayList<>());
        intent.setAlbumIds(new ArrayList<>());
        intent.setKeywords(new ArrayList<>());
        intent.setFilenameKeywords(new ArrayList<>());
        intent.setMust(new ArrayList<>());
        intent.setShould(new ArrayList<>());
        intent.setMustNot(new ArrayList<>());
        intent.setResultTypes(new ArrayList<>(resultTypes));
        intent.setIncludeHidden(false);
        intent.setNeedAnswer(true);
        intent.setAnswerPrompt(answerPrompt);
        intent.setExplanation(explanation);
        return intent;
    }

    public Map<String, Object> buildSummaryMetrics(String periodLabel,
                                                   long totalMatched,
                                                   List<String> summaryItems) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("periodLabel", periodLabel);
        metrics.put("totalMatched", totalMatched);
        metrics.put("summaryItems", summaryItems == null ? Collections.emptyList() : summaryItems);
        return metrics;
    }

    public Map<String, Object> buildCountMetrics(String periodLabel,
                                                 long totalMatched,
                                                 int albumSize) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("periodLabel", periodLabel);
        metrics.put("totalMatched", totalMatched);
        metrics.put("albumSize", albumSize);
        return metrics;
    }

    public Map<String, Object> buildAlbumMetrics(String periodLabel,
                                                 long totalMatched,
                                                 List<String> summaryItems) {
        return buildSummaryMetrics(periodLabel, totalMatched, summaryItems);
    }
}
