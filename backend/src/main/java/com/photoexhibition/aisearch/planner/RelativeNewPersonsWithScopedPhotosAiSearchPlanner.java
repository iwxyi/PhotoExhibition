package com.photoexhibition.aisearch.planner;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.plan.AiSearchPlanStep;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class RelativeNewPersonsWithScopedPhotosAiSearchPlanner {

    private static final Set<String> CAMERA_BRAND_CUES = Set.of(
        "canon", "佳能", "nikon", "尼康", "sony", "索尼", "fujifilm", "fuji", "富士",
        "leica", "徕卡", "panasonic", "lumix", "松下", "olympus", "omsystem", "奥林巴斯",
        "ricoh", "理光", "pentax", "宾得", "hasselblad", "哈苏", "dji", "大疆"
    );

    public boolean supports(String query, List<String> cameraCandidates, List<String> lensCandidates) {
        return !resolve(query, cameraCandidates, lensCandidates).isEmpty();
    }

    public AiSearchPlan plan(String query, List<String> cameraCandidates, List<String> lensCandidates, int offset, int limit) {
        ScopedPhotoMatch match = resolve(query, cameraCandidates, lensCandidates);
        if (match.isEmpty()) {
            throw new IllegalArgumentException("query does not match relative new persons with scoped photos planner");
        }
        int currentYear = LocalDate.now().getYear();
        int targetYear = query.contains("前年")
            ? currentYear - 2
            : query.contains("今年") ? currentYear : currentYear - 1;
        return planForScope(
            query,
            targetYear,
            new ArrayList<>(match.cameraModels),
            new ArrayList<>(match.lensModels),
            new ArrayList<>(match.keywords),
            offset,
            limit
        );
    }

    public AiSearchPlan planForScope(String query,
                                     int targetYear,
                                     List<String> cameraModels,
                                     List<String> lensModels,
                                     List<String> scopeKeywords,
                                     int offset,
                                     int limit) {
        String targetStart = targetYear + "-01-01";
        String targetEnd = targetYear + "-12-31";
        String baselineEnd = (targetYear - 1) + "-12-31";

        AiSearchPlan plan = new AiSearchPlan();
        plan.setQuery(query);
        plan.setQueryMode("analysis");
        plan.setPlanType("relative_new_persons_with_scoped_photos");
        plan.setResultTypes(List.of("persons"));
        plan.getMetadata().put("targetYear", targetYear);
        plan.getMetadata().put("cameraModels", new ArrayList<>(cameraModels));
        plan.getMetadata().put("lensModels", new ArrayList<>(lensModels));
        plan.getMetadata().put("scopeKeywords", new ArrayList<>(scopeKeywords));
        plan.getMetadata().put("intent", "relative_new_persons_with_scoped_photos");

        plan.getSteps().add(filterStep("target_range_photos", "筛选目标年份中的可见照片", Map.of(
            "startDate", targetStart,
            "endDate", targetEnd
        )));
        plan.getSteps().add(filterStep("baseline_photos", "筛选目标年份之前的可见照片", Map.of(
            "endDate", baselineEnd
        )));
        plan.getSteps().add(aggregatePersonsStep("target_persons", "target_range_photos", "聚合目标年份的人物出现情况"));
        plan.getSteps().add(aggregatePersonsStep("baseline_persons", "baseline_photos", "聚合历史人物出现情况"));

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

        plan.getSteps().add(filterStep("scoped_photos", "筛选目标年份内符合地点/器材范围的可见照片", Map.of(
            "startDate", targetStart,
            "endDate", targetEnd,
            "cameraModels", new ArrayList<>(cameraModels),
            "lensModels", new ArrayList<>(lensModels),
            "keywords", new ArrayList<>(scopeKeywords)
        )));
        plan.getSteps().add(aggregatePersonsStep("scoped_persons", "scoped_photos", "聚合范围内的人物出现情况"));

        AiSearchPlanStep intersection = new AiSearchPlanStep();
        intersection.setId("scoped_new_persons");
        intersection.setOperator("set_intersection");
        intersection.setInputRef("scoped_persons");
        intersection.setOutputKey("scoped_new_persons");
        intersection.getDependsOn().add("scoped_persons");
        intersection.getDependsOn().add("new_persons");
        intersection.setDescription("保留同时属于新认识人物和范围照片的人物");
        intersection.setArgs(Map.of("compareWithRef", "new_persons"));
        plan.getSteps().add(intersection);

        AiSearchPlanStep sorted = new AiSearchPlanStep();
        sorted.setId("sorted_scoped_new_persons");
        sorted.setOperator("sort");
        sorted.setInputRef("scoped_new_persons");
        sorted.setOutputKey("sorted_scoped_new_persons");
        sorted.getDependsOn().add("scoped_new_persons");
        sorted.setDescription("按范围内出现频次与最近出现时间排序");
        sorted.setArgs(Map.of(
            "field", "matchedPhotoCount",
            "direction", "desc",
            "secondaryField", "matchedLastSeen",
            "secondaryDirection", "desc"
        ));
        plan.getSteps().add(sorted);

        AiSearchPlanStep limited = new AiSearchPlanStep();
        limited.setId("limited_scoped_new_persons");
        limited.setOperator("limit");
        limited.setInputRef("sorted_scoped_new_persons");
        limited.setOutputKey("limited_scoped_new_persons");
        limited.getDependsOn().add("sorted_scoped_new_persons");
        limited.setDescription("分页截断范围内的新认识人物结果");
        limited.setArgs(Map.of(
            "offset", Math.max(0, offset),
            "size", Math.max(1, limit)
        ));
        plan.getSteps().add(limited);

        return plan;
    }

    private AiSearchPlanStep filterStep(String id, String description, Map<String, Object> args) {
        AiSearchPlanStep step = new AiSearchPlanStep();
        step.setId(id);
        step.setOperator("filter_photos");
        step.setOutputKey(id);
        step.setDescription(description);
        step.setArgs(args);
        return step;
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

    private ScopedPhotoMatch resolve(String query, List<String> cameraCandidates, List<String> lensCandidates) {
        if (!supportsRelativeNewPersonsShape(query)) {
            return ScopedPhotoMatch.empty();
        }
        String normalized = query == null ? "" : query.toLowerCase();
        boolean hasTechnicalCue = CAMERA_BRAND_CUES.stream().anyMatch(normalized::contains);
        if (!hasTechnicalCue) {
            return ScopedPhotoMatch.empty();
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

        LinkedHashSet<String> keywords = extractScopeKeywords(query, cameraModels, lensModels);
        return keywords.isEmpty() || (cameraModels.isEmpty() && lensModels.isEmpty())
            ? ScopedPhotoMatch.empty()
            : new ScopedPhotoMatch(cameraModels, lensModels, keywords);
    }

    private boolean supportsRelativeNewPersonsShape(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        boolean refersToPersons = query.contains("谁") || query.contains("哪些人")
            || query.contains("哪些人物") || query.contains("人物")
            || query.contains("的人") || query.contains("人里");
        boolean refersToNewness = query.contains("新认识") || query.contains("新出现")
            || query.contains("第一次出现") || query.contains("首次出现") || query.contains("才出现");
        boolean refersToRelativeYear = query.contains("去年") || query.contains("前年") || query.contains("今年");
        return refersToPersons && refersToNewness && refersToRelativeYear;
    }

    private LinkedHashSet<String> extractScopeKeywords(String query, Set<String> cameraModels, Set<String> lensModels) {
        String normalized = stripScopeNoise(query);
        for (String cameraModel : cameraModels) {
            normalized = normalized.replace(stripTechnicalNoise(cameraModel), " ");
        }
        for (String lensModel : lensModels) {
            normalized = normalized.replace(stripTechnicalNoise(lensModel), " ");
        }

        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        for (String token : normalized.split("\\s+")) {
            String keyword = normalizeKeyword(token);
            if (keyword != null) {
                keywords.add(keyword);
            }
        }
        return keywords;
    }

    private String normalizeKeyword(String token) {
        if (token == null) {
            return null;
        }
        String value = token.trim().toLowerCase();
        if (value.length() < 2 || value.length() > 12) {
            return null;
        }
        if (value.chars().allMatch(Character::isDigit)) {
            return null;
        }
        if (CAMERA_BRAND_CUES.contains(value) || "canon".equals(value) || "sony".equals(value)
            || "nikon".equals(value) || "fujifilm".equals(value) || "panasonic".equals(value)
            || "leica".equals(value) || "olympus".equals(value) || "dji".equals(value)) {
            return null;
        }
        return value;
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

    private int scoreCandidate(String subject, String candidate) {
        if (subject == null || candidate == null || subject.isBlank() || candidate.isBlank()) {
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

    private String stripScopeNoise(String query) {
        String result = query == null ? "" : query
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
            .replace("后续出现次数最多", " ")
            .replace("后来出现次数最多", " ")
            .replace("之后出现次数最多", " ")
            .replace("后续最活跃", " ")
            .replace("后来最活跃", " ")
            .replace("之后最活跃", " ")
            .replace("还经常出现", " ")
            .replace("还常出现", " ")
            .replace("还出现", " ")
            .replace("仍然出现", " ")
            .replace("今年还有", " ")
            .replace("且", " ")
            .replace("用", " ")
            .replace("在", " ")
            .replace("里", " ")
            .replace("的", " ")
            .replace("，", " ")
            .replace(",", " ")
            .replace("。", " ")
            .replace("、", " ")
            .replace("有哪", " ")
            .replace("哪些", " ")
            .replace("是", " ")
            .toLowerCase();
        result = result.replace("佳能", "canon")
            .replace("索尼", "sony")
            .replace("尼康", "nikon")
            .replace("富士", "fujifilm")
            .replace("松下", "panasonic")
            .replace("徕卡", "leica")
            .replace("奥林巴斯", "olympus")
            .replace("大疆", "dji");
        return result.replaceAll("\\s+", " ").trim();
    }

    private String stripTechnicalNoise(String query) {
        return stripScopeNoise(query);
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

    private static class ScopedPhotoMatch {
        private final LinkedHashSet<String> cameraModels;
        private final LinkedHashSet<String> lensModels;
        private final LinkedHashSet<String> keywords;

        private ScopedPhotoMatch(LinkedHashSet<String> cameraModels,
                                 LinkedHashSet<String> lensModels,
                                 LinkedHashSet<String> keywords) {
            this.cameraModels = cameraModels;
            this.lensModels = lensModels;
            this.keywords = keywords;
        }

        private static ScopedPhotoMatch empty() {
            return new ScopedPhotoMatch(new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>());
        }

        private boolean isEmpty() {
            return keywords.isEmpty() || (cameraModels.isEmpty() && lensModels.isEmpty());
        }
    }
}
