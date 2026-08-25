package com.photoexhibition.aisearch.orchestration;

import com.photoexhibition.aisearch.executor.AiSearchExecutionResult;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AiSearchBodyChangeSupport {

    public BodyChangeAnalysis analyze(String query,
                                      Long personId,
                                      String personName,
                                      int currentYear) {
        BodyChangeAnalysis analysis = new BodyChangeAnalysis();
        analysis.personId = personId;
        analysis.personName = personName;
        analysis.endYear = currentYear;
        analysis.startYear = currentYear - 2;

        if (query == null) {
            analysis.explanation = buildExplanation(analysis);
            return analysis;
        }

        if (query.contains("这两年") || query.contains("近两年")) {
            analysis.startYear = currentYear - 2;
            analysis.endYear = currentYear;
        } else if (query.contains("这三年") || query.contains("近三年")) {
            analysis.startYear = currentYear - 3;
            analysis.endYear = currentYear;
        } else if (query.contains("近五年") || query.contains("这五年")) {
            analysis.startYear = currentYear - 5;
            analysis.endYear = currentYear;
        } else if (query.contains("近几年")) {
            analysis.startYear = currentYear - 3;
            analysis.endYear = currentYear;
        } else if (query.contains("近一年") || query.contains("这一年")) {
            analysis.startYear = currentYear - 1;
            analysis.endYear = currentYear;
        } else if (query.contains("去年")) {
            analysis.startYear = currentYear - 1;
            analysis.endYear = currentYear - 1;
        } else if (query.contains("今年")) {
            analysis.startYear = currentYear;
            analysis.endYear = currentYear;
        } else if (query.contains("前年")) {
            analysis.startYear = currentYear - 2;
            analysis.endYear = currentYear - 2;
        }

        if (query.contains("年")) {
            String yearStr = query.replaceAll(".*?(\\d{2,4})年.*", "$1");
            try {
                int year = normalizeYear(yearStr, currentYear);
                if (year >= 2000 && year <= currentYear + 1) {
                    analysis.startYear = year;
                    analysis.endYear = year;
                    String allYears = query.replaceAll("[^\\d]", " ");
                    String[] parts = allYears.trim().split("\\s+");
                    for (String part : parts) {
                        if (part.length() < 2) {
                            continue;
                        }
                        try {
                            int candidateYear = normalizeYear(part, currentYear);
                            if (candidateYear >= 2000 && candidateYear <= currentYear + 1 && candidateYear != year) {
                                analysis.startYear = Math.min(year, candidateYear);
                                analysis.endYear = Math.max(year, candidateYear);
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }

        analysis.explanation = buildExplanation(analysis);
        return analysis;
    }

    public Map<String, Object> buildAnalysisData(BodyChangeAnalysis analysis,
                                                 AiSearchExecutionResult executionResult,
                                                 String answer) {
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) executionResult.getFinalOutputs()
            .getOrDefault("body_change_metrics", Collections.emptyMap());
        Map<String, Object> analysisData = new LinkedHashMap<>();
        analysisData.put("analysisType", "body_change");
        analysisData.put("personId", analysis.personId);
        analysisData.put("personName", analysis.personName);
        analysisData.put("startYear", analysis.startYear);
        analysisData.put("endYear", analysis.endYear);
        analysisData.put("totalPhotos", metrics.get("totalPhotos"));
        analysisData.put("avgFaceArea", metrics.get("avgFaceArea"));
        analysisData.put("avgFaceWidth", metrics.get("avgFaceWidth"));
        analysisData.put("avgFaceHeight", metrics.get("avgFaceHeight"));
        analysisData.put("avgAspectRatio", metrics.get("avgAspectRatio"));
        analysisData.put("yearlyStats", metrics.getOrDefault("yearlyStats", Collections.emptyList()));
        analysisData.put("trend", metrics.get("trend"));
        analysisData.put("conclusion", answer);
        analysisData.put("changePercent", metrics.get("changePercent"));
        analysisData.put("firstPeriod", metrics.get("firstPeriod"));
        analysisData.put("lastPeriod", metrics.get("lastPeriod"));
        return analysisData;
    }

    private int normalizeYear(String yearText, int currentYear) {
        int year = yearText.length() == 2 ? 2000 + Integer.parseInt(yearText) : Integer.parseInt(yearText);
        if (year < 2000 || year > currentYear + 1) {
            throw new NumberFormatException("year out of range");
        }
        return year;
    }

    private String buildExplanation(BodyChangeAnalysis analysis) {
        return String.format(
            "分析 %s 在 %d-%d 年间的体型/面部变化",
            analysis.personName != null ? analysis.personName : "该人物",
            analysis.startYear,
            analysis.endYear
        );
    }

    public static class BodyChangeAnalysis {
        private Long personId;
        private String personName;
        private int startYear;
        private int endYear;
        private String explanation;

        public Long getPersonId() {
            return personId;
        }

        public String getPersonName() {
            return personName;
        }

        public int getStartYear() {
            return startYear;
        }

        public int getEndYear() {
            return endYear;
        }

        public String getExplanation() {
            return explanation;
        }
    }
}
