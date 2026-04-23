package com.photoexhibition.aisearch.planner;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class RelativeNewPersonsWithTechnicalScopeAiSearchPlanner {

    private static final Set<String> CAMERA_BRAND_CUES = Set.of(
        "canon", "佳能", "nikon", "尼康", "sony", "索尼", "fujifilm", "fuji", "富士",
        "leica", "徕卡", "panasonic", "lumix", "松下", "olympus", "omsystem", "奥林巴斯",
        "ricoh", "理光", "pentax", "宾得", "hasselblad", "哈苏", "dji", "大疆"
    );

    public boolean supports(String query, List<String> cameraCandidates, List<String> lensCandidates) {
        return !resolve(query, cameraCandidates, lensCandidates).isEmpty();
    }

    public AiSearchPlan plan(String query, List<String> cameraCandidates, List<String> lensCandidates, int offset, int limit) {
        TechnicalScopeMatch match = resolve(query, cameraCandidates, lensCandidates);
        if (match.isEmpty()) {
            throw new IllegalArgumentException("query does not match relative new persons with technical scope planner");
        }
        int currentYear = LocalDate.now().getYear();
        int targetYear = query.contains("前年")
            ? currentYear - 2
            : query.contains("今年") ? currentYear : currentYear - 1;
        return planForScope(query, targetYear, new ArrayList<>(match.cameraModels), new ArrayList<>(match.lensModels), offset, limit);
    }

    public AiSearchPlan planForScope(String query,
                                     int targetYear,
                                     List<String> cameraModels,
                                     List<String> lensModels,
                                     int offset,
                                     int limit) {
        String targetStart = targetYear + "-01-01";
        String targetEnd = targetYear + "-12-31";
        String baselineEnd = (targetYear - 1) + "-12-31";

        AiSearchPlan plan = new AiSearchPlan();
        plan.setQuery(query);
        plan.setQueryMode("analysis");
        plan.setPlanType("relative_new_persons_with_technical_scope");
        plan.setResultTypes(List.of("persons"));
        plan.getMetadata().put("targetYear", targetYear);
        plan.getMetadata().put("cameraModels", new ArrayList<>(cameraModels));
        plan.getMetadata().put("lensModels", new ArrayList<>(lensModels));
        plan.getMetadata().put("intent", "relative_new_persons_with_technical_scope");

        AiSearchPlanStep targetRange = new AiSearchPlanStep();
        targetRange.setId("target_range_photos");
        targetRange.setOperator("filter_photos");
        targetRange.setOutputKey("target_range_photos");
        targetRange.setDescription("筛选目标年份中的可见照片");
        targetRange.setArgs(Map.of(
            "startDate", targetStart,
            "endDate", targetEnd
        ));
        plan.getSteps().add(targetRange);

        AiSearchPlanStep baselineRange = new AiSearchPlanStep();
        baselineRange.setId("baseline_photos");
        baselineRange.setOperator("filter_photos");
        baselineRange.setOutputKey("baseline_photos");
        baselineRange.setDescription("筛选目标年份之前的可见照片");
        baselineRange.setArgs(Map.of("endDate", baselineEnd));
        plan.getSteps().add(baselineRange);

        AiSearchPlanStep targetPersons = aggregatePersonsStep("target_persons", "target_range_photos", "聚合目标年份的人物出现情况");
        plan.getSteps().add(targetPersons);

        AiSearchPlanStep baselinePersons = aggregatePersonsStep("baseline_persons", "baseline_photos", "聚合历史人物出现情况");
        plan.getSteps().add(baselinePersons);

        AiSearchPlanStep difference = new AiSearchPlanStep();
        difference.setId("new_persons");
        difference.setOperator("set_difference");
        difference.setInputRef("target_persons");
        difference.setOutputKey("new_persons");
        difference.getDependsOn().add("target_persons");
        difference.getDependsOn().add("baseline_persons");
        difference.setDescription("剔除目标年份之前已经出现过的人物");
        difference.setArgs(Map.of("compareWithRef", "baseline_persons"));
        plan.getSteps().add(difference);

        AiSearchPlanStep technicalPhotos = new AiSearchPlanStep();
        technicalPhotos.setId("technical_scope_photos");
        technicalPhotos.setOperator("filter_photos");
        technicalPhotos.setOutputKey("technical_scope_photos");
        technicalPhotos.setDescription("筛选目标年份内符合器材条件的可见照片");
        technicalPhotos.setArgs(Map.of(
            "startDate", targetStart,
            "endDate", targetEnd,
            "cameraModels", new ArrayList<>(cameraModels),
            "lensModels", new ArrayList<>(lensModels)
        ));
        plan.getSteps().add(technicalPhotos);

        AiSearchPlanStep technicalPersons = aggregatePersonsStep(
            "technical_scope_persons",
            "technical_scope_photos",
            "聚合目标年份内器材范围的人物出现情况"
        );
        plan.getSteps().add(technicalPersons);

        AiSearchPlanStep intersected = new AiSearchPlanStep();
        intersected.setId("filtered_new_persons");
        intersected.setOperator("set_intersection");
        intersected.setInputRef("new_persons");
        intersected.setOutputKey("filtered_new_persons");
        intersected.getDependsOn().add("new_persons");
        intersected.getDependsOn().add("technical_scope_persons");
        intersected.setDescription("保留同时属于新认识人物和器材范围的人物");
        intersected.setArgs(Map.of("compareWithRef", "technical_scope_persons"));
        plan.getSteps().add(intersected);

        AiSearchPlanStep sorted = new AiSearchPlanStep();
        sorted.setId("sorted_filtered_new_persons");
        sorted.setOperator("sort");
        sorted.setInputRef("filtered_new_persons");
        sorted.setOutputKey("sorted_filtered_new_persons");
        sorted.getDependsOn().add("filtered_new_persons");
        sorted.setDescription("按目标年份出现频次与最近出现时间排序");
        sorted.setArgs(Map.of(
            "field", "matchedPhotoCount",
            "direction", "desc",
            "secondaryField", "matchedLastSeen",
            "secondaryDirection", "desc"
        ));
        plan.getSteps().add(sorted);

        AiSearchPlanStep limited = new AiSearchPlanStep();
        limited.setId("limited_filtered_new_persons");
        limited.setOperator("limit");
        limited.setInputRef("sorted_filtered_new_persons");
        limited.setOutputKey("limited_filtered_new_persons");
        limited.getDependsOn().add("sorted_filtered_new_persons");
        limited.setDescription("分页截断器材范围内的新认识人物结果");
        limited.setArgs(Map.of(
            "offset", Math.max(0, offset),
            "size", Math.max(1, limit)
        ));
        plan.getSteps().add(limited);

        return plan;
    }

    private AiSearchPlanStep aggregatePersonsStep(String id, String inputRef, String description) {
        AiSearchPlanStep step = new AiSearchPlanStep();
        step.setId(id);
        step.setOperator("aggregate_persons");
        step.setInputRef(inputRef);
        step.setOutputKey(id);
        step.getDependsOn().add(inputRef);
        step.setDescription(description);
        return step;
    }

    private TechnicalScopeMatch resolve(String query, List<String> cameraCandidates, List<String> lensCandidates) {
        if (!supportsRelativeNewPersonsShape(query)) {
            return TechnicalScopeMatch.empty();
        }
        String normalized = query == null ? "" : query.toLowerCase();
        boolean hasTechnicalCue = CAMERA_BRAND_CUES.stream().anyMatch(cue -> normalized.contains(cue));
        if (!hasTechnicalCue) {
            return TechnicalScopeMatch.empty();
        }

        LinkedHashSet<String> cameraModels = new LinkedHashSet<>();
        LinkedHashSet<String> lensModels = new LinkedHashSet<>();
        String camera = bestTechnicalCandidate(query, cameraCandidates);
        if (camera != null) {
            cameraModels.add(camera);
        }
        String lens = bestTechnicalCandidate(query, lensCandidates);
        if (lens != null) {
            lensModels.add(lens);
        }
        return cameraModels.isEmpty() && lensModels.isEmpty()
            ? TechnicalScopeMatch.empty()
            : new TechnicalScopeMatch(cameraModels, lensModels);
    }

    private boolean supportsRelativeNewPersonsShape(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        boolean refersToPersons = query.contains("谁") || query.contains("哪些人")
            || query.contains("哪些人物") || query.contains("人物");
        boolean refersToNewness = query.contains("新认识") || query.contains("新出现")
            || query.contains("第一次出现") || query.contains("首次出现") || query.contains("才出现");
        boolean refersToRelativeYear = query.contains("去年") || query.contains("前年") || query.contains("今年");
        return refersToPersons && refersToNewness && refersToRelativeYear;
    }

    private String bestTechnicalCandidate(String subject, List<String> candidates) {
        if (subject == null || subject.isBlank() || candidates == null || candidates.isEmpty()) {
            return null;
        }
        String normalizedSubject = stripTechnicalNoise(subject);
        int bestScore = 0;
        String bestCandidate = null;
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            int score = scoreCandidate(normalizedSubject, stripTechnicalNoise(candidate));
            if (score > bestScore) {
                bestScore = score;
                bestCandidate = candidate;
            }
        }
        return bestScore > 0 ? bestCandidate : null;
    }

    private String stripTechnicalNoise(String query) {
        if (query == null) {
            return "";
        }
        String result = query
            .replace("去年", " ")
            .replace("今年", " ")
            .replace("前年", " ")
            .replace("新认识", " ")
            .replace("第一次出现", " ")
            .replace("首次出现", " ")
            .replace("人物", " ")
            .replace("哪些人", " ")
            .replace("哪些人物", " ")
            .replace("有谁", " ")
            .replace("是谁", " ")
            .replace("拍到", " ")
            .replace("拍到的", " ")
            .replace("拍的", " ")
            .replace("用", " ")
            .replace("里", " ")
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

    private static final class TechnicalScopeMatch {
        private final LinkedHashSet<String> cameraModels;
        private final LinkedHashSet<String> lensModels;

        private TechnicalScopeMatch(LinkedHashSet<String> cameraModels, LinkedHashSet<String> lensModels) {
            this.cameraModels = cameraModels;
            this.lensModels = lensModels;
        }

        private static TechnicalScopeMatch empty() {
            return new TechnicalScopeMatch(new LinkedHashSet<>(), new LinkedHashSet<>());
        }

        private boolean isEmpty() {
            return cameraModels.isEmpty() && lensModels.isEmpty();
        }
    }
}
