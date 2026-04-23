package com.photoexhibition.aisearch.planner;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class TechnicalDisjunctionAiSearchPlanner {

    private static final Set<String> DISJUNCTION_CUES = Set.of("或者", "或", "/", "、");

    public boolean supports(String query, List<String> cameraCandidates, List<String> lensCandidates) {
        return !resolve(query, cameraCandidates, lensCandidates).isEmpty();
    }

    public AiSearchPlan plan(String query, List<String> cameraCandidates, List<String> lensCandidates, int offset, int limit) {
        TechnicalDisjunctionMatch match = resolve(query, cameraCandidates, lensCandidates);
        if (match.isEmpty()) {
            throw new IllegalArgumentException("query does not match technical disjunction planner");
        }

        AiSearchPlan plan = new AiSearchPlan();
        plan.setQuery(query);
        plan.setQueryMode("simple_search");
        plan.setPlanType("technical_disjunction");
        plan.setResultTypes(List.of("photos"));
        plan.getMetadata().put("cameraModels", new ArrayList<>(match.cameraModels));
        plan.getMetadata().put("lensModels", new ArrayList<>(match.lensModels));

        AiSearchPlanStep filter = new AiSearchPlanStep();
        filter.setId("technical_candidates");
        filter.setOperator("filter_photos");
        filter.setOutputKey("technical_candidates");
        filter.setDescription("按相机或镜头型号做并集检索");
        filter.setArgs(Map.of(
            "cameraModels", new ArrayList<>(match.cameraModels),
            "lensModels", new ArrayList<>(match.lensModels)
        ));
        plan.getSteps().add(filter);

        AiSearchPlanStep limited = new AiSearchPlanStep();
        limited.setId("paged_technical_candidates");
        limited.setOperator("limit");
        limited.setInputRef("technical_candidates");
        limited.setOutputKey("paged_technical_candidates");
        limited.getDependsOn().add("technical_candidates");
        limited.setDescription("分页截断器材并集检索结果");
        limited.setArgs(Map.of(
            "offset", Math.max(0, offset),
            "size", Math.max(1, limit)
        ));
        plan.getSteps().add(limited);

        return plan;
    }

    private TechnicalDisjunctionMatch resolve(String query, List<String> cameraCandidates, List<String> lensCandidates) {
        if (query == null || query.isBlank() || !containsDisjunctionCue(query)) {
            return TechnicalDisjunctionMatch.empty();
        }

        List<String> segments = splitDisjunctiveQuery(query);
        if (segments.size() < 2) {
            return TechnicalDisjunctionMatch.empty();
        }

        LinkedHashSet<String> cameraModels = new LinkedHashSet<>();
        LinkedHashSet<String> lensModels = new LinkedHashSet<>();
        for (String segment : segments) {
            String subject = stripDirectQueryNoise(segment);
            if (subject.isBlank()) {
                continue;
            }
            String camera = bestTechnicalCandidate(subject, cameraCandidates);
            if (camera != null) {
                cameraModels.add(camera);
            }
            String lens = bestTechnicalCandidate(subject, lensCandidates);
            if (lens != null) {
                lensModels.add(lens);
            }
        }

        if (cameraModels.size() + lensModels.size() < 2) {
            return TechnicalDisjunctionMatch.empty();
        }
        return new TechnicalDisjunctionMatch(cameraModels, lensModels);
    }

    private boolean containsDisjunctionCue(String query) {
        for (String cue : DISJUNCTION_CUES) {
            if (query.contains(cue)) {
                return true;
            }
        }
        return false;
    }

    private List<String> splitDisjunctiveQuery(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        String normalized = query.replace("或者", "|")
            .replace("或", "|")
            .replace("/", "|")
            .replace("、", "|");
        String[] parts = normalized.split("\\|");
        List<String> segments = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.trim().isBlank()) {
                segments.add(part.trim());
            }
        }
        return segments;
    }

    private String stripDirectQueryNoise(String query) {
        if (query == null) {
            return "";
        }
        String result = query
            .replace("去年", " ")
            .replace("今年", " ")
            .replace("前年", " ")
            .replace("一下", " ")
            .replace("看看", " ")
            .replace("搜索", " ")
            .replace("搜", " ")
            .replace("查找", " ")
            .replace("照片", " ")
            .replace("图片", " ")
            .replace("相片", " ")
            .replace("的", " ")
            .toLowerCase();
        result = result.replace("佳能", "canon")
            .replace("索尼", "sony")
            .replace("尼康", "nikon")
            .replace("富士", "fujifilm")
            .replace("松下", "panasonic")
            .replace("徕卡", "leica")
            .replace("奥林巴斯", "olympus");
        return result.replaceAll("\\s+", " ").trim();
    }

    private String bestTechnicalCandidate(String subject, List<String> candidates) {
        if (subject == null || subject.isBlank() || candidates == null || candidates.isEmpty()) {
            return null;
        }
        String normalizedSubject = stripDirectQueryNoise(subject);
        int bestScore = 0;
        String bestCandidate = null;
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            int score = scoreCandidate(normalizedSubject, stripDirectQueryNoise(candidate));
            if (score > bestScore) {
                bestScore = score;
                bestCandidate = candidate;
            }
        }
        return bestScore > 0 ? bestCandidate : null;
    }

    private int scoreCandidate(String subject, String candidate) {
        if (subject.isBlank() || candidate.isBlank()) {
            return 0;
        }
        if (candidate.equals(subject)) {
            return 100;
        }
        if (candidate.contains(subject) || subject.contains(candidate)) {
            return 80;
        }

        int score = 0;
        for (String token : subject.split("\\s+")) {
            if (!token.isBlank() && candidate.contains(token)) {
                score += token.length();
            }
        }
        String compactSubject = subject.replaceAll("[^a-z0-9]", "");
        String compactCandidate = candidate.replaceAll("[^a-z0-9]", "");
        if (!compactSubject.isBlank() && isSubsequence(compactSubject, compactCandidate)) {
            score += compactSubject.length() * 3;
        }
        return score;
    }

    private boolean isSubsequence(String needle, String haystack) {
        if (needle.isBlank() || haystack.isBlank()) {
            return false;
        }
        int cursor = 0;
        for (int i = 0; i < haystack.length() && cursor < needle.length(); i++) {
            if (haystack.charAt(i) == needle.charAt(cursor)) {
                cursor++;
            }
        }
        return cursor == needle.length();
    }

    private static final class TechnicalDisjunctionMatch {
        private final LinkedHashSet<String> cameraModels;
        private final LinkedHashSet<String> lensModels;

        private TechnicalDisjunctionMatch(LinkedHashSet<String> cameraModels, LinkedHashSet<String> lensModels) {
            this.cameraModels = cameraModels;
            this.lensModels = lensModels;
        }

        private static TechnicalDisjunctionMatch empty() {
            return new TechnicalDisjunctionMatch(new LinkedHashSet<>(), new LinkedHashSet<>());
        }

        private boolean isEmpty() {
            return cameraModels.isEmpty() && lensModels.isEmpty();
        }
    }
}
