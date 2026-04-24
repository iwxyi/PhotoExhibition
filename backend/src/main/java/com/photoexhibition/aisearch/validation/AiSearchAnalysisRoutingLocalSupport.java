package com.photoexhibition.aisearch.validation;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class AiSearchAnalysisRoutingLocalSupport {

    private static final Set<String> THEME_ANALYSIS_CUES = Set.of(
        "主题", "题材", "拍什么", "什么主题", "什么题材"
    );
    private static final Set<String> LOCATION_ANALYSIS_CUES = Set.of(
        "哪里拍", "哪里拍过", "在哪拍", "地点", "地方", "位置"
    );
    private static final Set<String> LOCATION_GENERAL_CUES = Set.of(
        "哪里", "哪儿", "哪边", "地点", "地方", "位置", "城市", "区", "县"
    );
    private static final Set<String> ALBUM_ANALYSIS_CUES = Set.of(
        "哪个相册", "哪些相册", "相册最多", "相册拍得最多"
    );
    private static final Set<String> MONTH_ANALYSIS_CUES = Set.of(
        "哪个月", "哪些月", "月份", "几月", "月拍"
    );
    private static final Set<String> COUNT_ANALYSIS_CUES = Set.of(
        "多少张", "几张", "多少次", "几次", "数量", "总数"
    );
    private static final Set<String> PERSON_OVERVIEW_ANALYSIS_CUES = Set.of(
        "有谁", "哪些人", "谁", "人物", "拍到了谁"
    );
    private static final Set<String> PERSON_COOCCURRENCE_ANALYSIS_CUES = Set.of(
        "和谁", "一起出现", "同框", "一起", "共同出现"
    );
    private static final Set<String> PERSON_PAIR_COOCCURRENCE_ANALYSIS_CUES = Set.of(
        "谁和谁", "两个人", "人物组合", "人物对"
    );
    private static final Set<String> DAY_ANALYSIS_CUES = Set.of(
        "哪天", "哪一天", "哪几天", "日期", "几号"
    );
    private static final Set<String> TAG_ANALYSIS_CUES = Set.of(
        "标签", "tag", "tags"
    );
    private static final Set<String> ANALYSIS_RANK_CUES = Set.of(
        "最多", "最常", "排行", "排名", "top"
    );
    private static final Set<String> YEAR_COMPARE_ANALYSIS_CUES = Set.of(
        "相比", "对比", "比较", "更多还是更少", "更少还是更多"
    );
    private static final Set<String> BODY_CHANGE_ANALYSIS_CUES = Set.of(
        "更胖", "更瘦", "胖了", "瘦了", "胖吗", "瘦吗", "胖瘦", "体型变化"
    );

    public LocalAnalysisRouting resolve(String query) {
        if (query == null || query.isBlank()) {
            return LocalAnalysisRouting.none();
        }
        Map<String, Integer> scores = scoreAnalysisTypes(query);
        String bestType = null;
        int bestScore = Integer.MIN_VALUE;
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            if (entry.getValue() > bestScore) {
                bestScore = entry.getValue();
                bestType = entry.getKey();
            }
        }
        if (bestType == null || bestScore < 4) {
            return LocalAnalysisRouting.none();
        }
        AiSearchAnalysisFallbackRequest fallbackRequest = new AiSearchAnalysisFallbackRequest();
        fallbackRequest.setRoutingType(bestType);
        fallbackRequest.setResolvedQuery(query);
        return new LocalAnalysisRouting(bestType, bestScore, fallbackRequest);
    }

    public int resolveBestScore(String query) {
        return scoreAnalysisTypes(query).values().stream().max(Integer::compareTo).orElse(0);
    }

    Map<String, Integer> scoreAnalysisTypes(String query) {
        String normalized = normalizeLooseText(query);
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("theme", 0);
        scores.put("location", 0);
        scores.put("album", 0);
        scores.put("month", 0);
        scores.put("count", 0);
        scores.put("person_cooccurrence", 0);
        scores.put("person_pair_cooccurrence", 0);
        scores.put("person", 0);
        scores.put("day", 0);
        scores.put("tag", 0);
        scores.put("year_compare", 0);
        scores.put("body_change", 0);

        boolean hasDayCue = containsCue(normalized, DAY_ANALYSIS_CUES);
        boolean hasMonthCue = containsCue(normalized, MONTH_ANALYSIS_CUES) || normalized.contains("月份");
        boolean hasBodyChangeCue = containsCue(normalized, BODY_CHANGE_ANALYSIS_CUES);

        if (containsCue(normalized, THEME_ANALYSIS_CUES)) {
            scores.computeIfPresent("theme", (key, value) -> value + 5);
        }
        if (containsCue(normalized, LOCATION_ANALYSIS_CUES)
            || (containsCue(normalized, LOCATION_GENERAL_CUES) && !hasDayCue && !hasMonthCue)) {
            scores.computeIfPresent("location", (key, value) -> value + 5);
        }
        if (containsCue(normalized, ALBUM_ANALYSIS_CUES) || normalized.contains("相册")) {
            scores.computeIfPresent("album", (key, value) -> value + 4);
        }
        if (hasMonthCue) {
            scores.computeIfPresent("month", (key, value) -> value + 4);
        }
        if (containsCue(normalized, COUNT_ANALYSIS_CUES) || normalized.contains("数量")) {
            scores.computeIfPresent("count", (key, value) -> value + 5);
        }
        if (containsCue(normalized, PERSON_COOCCURRENCE_ANALYSIS_CUES)
            && containsCue(normalized, PERSON_OVERVIEW_ANALYSIS_CUES)) {
            scores.computeIfPresent("person_cooccurrence", (key, value) -> value + 8);
        }
        if (containsCue(normalized, PERSON_PAIR_COOCCURRENCE_ANALYSIS_CUES)) {
            scores.computeIfPresent("person_pair_cooccurrence", (key, value) -> value + 9);
        }
        if (containsCue(normalized, PERSON_OVERVIEW_ANALYSIS_CUES)
            && !containsCue(normalized, BODY_CHANGE_ANALYSIS_CUES)
            && !containsCue(normalized, YEAR_COMPARE_ANALYSIS_CUES)
            && !containsCue(normalized, PERSON_COOCCURRENCE_ANALYSIS_CUES)
            && !containsCue(normalized, PERSON_PAIR_COOCCURRENCE_ANALYSIS_CUES)) {
            scores.computeIfPresent("person", (key, value) -> value + 5);
        }
        if (hasDayCue) {
            scores.computeIfPresent("day", (key, value) -> value + 8);
        }
        if (containsCue(normalized, TAG_ANALYSIS_CUES)) {
            scores.computeIfPresent("tag", (key, value) -> value + 6);
        }
        if (containsCue(normalized, YEAR_COMPARE_ANALYSIS_CUES)) {
            scores.computeIfPresent("year_compare", (key, value) -> value + 6);
        }
        if (hasBodyChangeCue) {
            boolean hasTimeCue = normalized.contains("年") || normalized.contains("月") || normalized.contains("以前")
                || normalized.contains("过去") || normalized.contains("之前") || normalized.contains("变化");
            if (hasTimeCue) {
                scores.computeIfPresent("body_change", (key, value) -> value + 10);
            } else {
                scores.computeIfPresent("body_change", (key, value) -> value + 6);
            }
        }

        if (containsCue(normalized, ANALYSIS_RANK_CUES)) {
            if (containsCue(normalized, PERSON_PAIR_COOCCURRENCE_ANALYSIS_CUES)) {
                scores.computeIfPresent("person_pair_cooccurrence", (key, value) -> value + 2);
            }
            if (containsCue(normalized, PERSON_COOCCURRENCE_ANALYSIS_CUES)) {
                scores.computeIfPresent("person_cooccurrence", (key, value) -> value + 2);
            }
            if (containsCue(normalized, PERSON_OVERVIEW_ANALYSIS_CUES)) {
                scores.computeIfPresent("person", (key, value) -> value + 1);
            }
            if (containsCue(normalized, LOCATION_GENERAL_CUES) && !hasDayCue && !hasMonthCue) {
                scores.computeIfPresent("location", (key, value) -> value + 2);
            }
            if (normalized.contains("主题") || normalized.contains("题材") || normalized.contains("拍什么")) {
                scores.computeIfPresent("theme", (key, value) -> value + 2);
            }
            if (normalized.contains("相册")) {
                scores.computeIfPresent("album", (key, value) -> value + 2);
            }
            if (normalized.contains("月")) {
                scores.computeIfPresent("month", (key, value) -> value + 2);
            }
            if (normalized.contains("标签")) {
                scores.computeIfPresent("tag", (key, value) -> value + 2);
            }
        }

        if (normalized.contains("去年") && normalized.contains("前年")) {
            scores.computeIfPresent("year_compare", (key, value) -> value + 3);
        }
        if (normalized.contains("今年") && normalized.contains("去年")) {
            scores.computeIfPresent("year_compare", (key, value) -> value + 3);
        }
        return scores;
    }

    private boolean containsCue(String text, Set<String> cues) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String cue : cues) {
            if (text.contains(cue)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeLooseText(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c > 127) {
                cleaned.append(Character.toLowerCase(c));
            }
        }
        return cleaned.toString().trim();
    }

    public static class LocalAnalysisRouting {
        private final String routingType;
        private final int score;
        private final AiSearchAnalysisFallbackRequest fallbackRequest;

        public LocalAnalysisRouting(String routingType, int score, AiSearchAnalysisFallbackRequest fallbackRequest) {
            this.routingType = routingType;
            this.score = score;
            this.fallbackRequest = fallbackRequest;
        }

        public String getRoutingType() {
            return routingType;
        }

        public int getScore() {
            return score;
        }

        public AiSearchAnalysisFallbackRequest getFallbackRequest() {
            return fallbackRequest;
        }

        public boolean isResolved() {
            return routingType != null && !routingType.isBlank();
        }

        public static LocalAnalysisRouting none() {
            return new LocalAnalysisRouting(null, 0, null);
        }
    }
}
