package com.photoexhibition.aisearch.validation;

import com.photoexhibition.dto.AiSearchAnalysisOperation;
import com.photoexhibition.dto.AiSearchAnalysisScope;
import com.photoexhibition.dto.AiSearchAnalysisSpec;
import com.photoexhibition.dto.AiSearchAnalysisSubject;
import com.photoexhibition.dto.AiSearchIntent;
import com.photoexhibition.entity.PersonProfile;
import com.photoexhibition.repository.PersonProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AiSearchAnalysisSpecNormalizer {

    private static final Set<String> STOP_WORDS = Set.of(
        "的", "在", "了", "是", "和", "与", "或", "我", "要", "找", "有",
        "把", "从", "到", "都", "也", "就", "而", "但", "又", "为",
        "被", "给", "让", "向", "用", "对", "这", "那", "中", "上",
        "下", "里", "个", "些", "吗", "呢", "吧", "啊", "哦",
        "一", "不", "人", "大", "小", "多", "少", "很", "最"
    );

    private final PersonProfileRepository personProfileRepository;

    public void normalize(AiSearchIntent intent) {
        if (intent == null || intent.getAnalysisSpec() == null) {
            return;
        }

        AiSearchAnalysisSpec spec = intent.getAnalysisSpec();
        if (spec.getOperation() == null) {
            return;
        }
        if (spec.getSubject() == null) {
            spec.setSubject(new AiSearchAnalysisSubject());
        }
        if (spec.getScope() == null) {
            spec.setScope(new AiSearchAnalysisScope());
        }

        spec.setSubjectType(defaultIfBlank(spec.getSubjectType(), "persons"));
        spec.getSubject().setType(normalizeAnalysisSpecType(spec.getSubject().getType()));
        spec.getScope().setType(defaultIfBlank(normalizeAnalysisSpecType(spec.getScope().getType()), "none"));
        spec.getScope().setCameraModels(normalizeKeywordList(spec.getScope().getCameraModels(), false));
        spec.getScope().setLensModels(normalizeKeywordList(spec.getScope().getLensModels(), false));
        spec.getScope().setScopeKeywords(normalizeKeywordList(spec.getScope().getScopeKeywords(), false));

        AiSearchAnalysisOperation operation = spec.getOperation();
        operation.setType(normalizeAnalysisSpecType(operation.getType()));
        operation.setDesiredTrend(defaultIfBlank(normalizeAnalysisSpecType(operation.getDesiredTrend()), operation.getDesiredTrend()));
        operation.setSubject(trimToNull(operation.getSubject()));
        operation.setAnchorPersonIds(distinctLongs(operation.getAnchorPersonIds()));
        operation.setAnchorPersonNames(distinctTexts(operation.getAnchorPersonNames()));

        if (isBlank(spec.getSubject().getType()) && isFilteredScopeOperation(operation.getType())) {
            spec.getSubject().setType("filtered_scope");
        }
        if (("person_cooccurrence".equals(operation.getType()) || "cooccurrence".equals(operation.getType()))
            && operation.getAnchorPersonIds().isEmpty()) {
            List<Long> personIds = new ArrayList<>(getEffectivePersonIds(intent));
            if (!personIds.isEmpty()) {
                operation.setAnchorPersonIds(List.of(personIds.get(0)));
            }
        }
        if (("person_cooccurrence".equals(operation.getType()) || "cooccurrence".equals(operation.getType()))
            && operation.getAnchorPersonNames().isEmpty()
            && !operation.getAnchorPersonIds().isEmpty()) {
            operation.setAnchorPersonNames(operation.getAnchorPersonIds().stream()
                .map(this::resolvePersonName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toList()));
        }
        if ("year_compare".equals(operation.getType()) && isBlank(operation.getSubject())) {
            operation.setSubject(trimToNull(buildKeywordSummary(intent)));
        }

        if ("filtered_scope".equals(spec.getSubject().getType())) {
            LinkedHashSet<String> resultTypes = new LinkedHashSet<>(normalizeResultTypes(intent));
            addDefaultResultTypesForFilteredScopeOperation(resultTypes, operation.getType());
            intent.setResultTypes(new ArrayList<>(resultTypes));
            intent.setNeedAnswer(true);
        }
    }

    private void addDefaultResultTypesForFilteredScopeOperation(Set<String> resultTypes, String operationType) {
        if (isBlank(operationType)) {
            return;
        }
        switch (operationType) {
            case "person_overview":
            case "person_cooccurrence":
                resultTypes.add("persons");
                resultTypes.add("photos");
                resultTypes.add("albums");
                break;
            case "person_pair_cooccurrence":
            case "count_overview":
            case "album_overview":
            case "month_overview":
            case "location_overview":
            case "day_overview":
            case "tag_overview":
            case "theme_overview":
            case "year_compare":
                resultTypes.add("photos");
                resultTypes.add("albums");
                break;
            default:
                break;
        }
    }

    private boolean isFilteredScopeOperation(String operationType) {
        return "count_overview".equals(operationType)
            || "person_overview".equals(operationType)
            || "person_cooccurrence".equals(operationType)
            || "person_pair_cooccurrence".equals(operationType)
            || "album_overview".equals(operationType)
            || "month_overview".equals(operationType)
            || "location_overview".equals(operationType)
            || "day_overview".equals(operationType)
            || "tag_overview".equals(operationType)
            || "theme_overview".equals(operationType)
            || "year_compare".equals(operationType);
    }

    private List<String> normalizeResultTypes(AiSearchIntent intent) {
        if (intent.getResultTypes() == null || intent.getResultTypes().isEmpty()) {
            return new ArrayList<>();
        }
        return intent.getResultTypes().stream()
            .map(this::normalizeAnalysisSpecType)
            .filter(value -> !isBlank(value))
            .distinct()
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private String buildKeywordSummary(AiSearchIntent intent) {
        List<String> keywords = normalizeKeywordList(intent.getKeywords(), false);
        if (keywords.isEmpty()) {
            return "相关内容";
        }
        return String.join(" ", keywords.stream().limit(3).collect(Collectors.toList()));
    }

    private List<String> normalizeKeywordList(List<String> values, boolean preserveShortTokens) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values == null) {
            return new ArrayList<>();
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!preserveShortTokens && STOP_WORDS.contains(trimmed)) {
                continue;
            }
            if (!preserveShortTokens && trimmed.length() == 1) {
                continue;
            }
            normalized.add(trimmed);
        }
        return new ArrayList<>(normalized);
    }

    private List<Long> getEffectivePersonIds(AiSearchIntent intent) {
        if (intent.getPersonIds() != null && !intent.getPersonIds().isEmpty()) {
            return intent.getPersonIds();
        }
        if (intent.getPersonId() != null) {
            return List.of(intent.getPersonId());
        }
        return List.of();
    }

    private String resolvePersonName(Long personId) {
        if (personId == null) {
            return "";
        }
        return personProfileRepository.findById(personId)
            .map(PersonProfile::getName)
            .orElse("");
    }

    private String normalizeAnalysisSpecType(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<Long> distinctLongs(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        return values.stream()
            .filter(java.util.Objects::nonNull)
            .distinct()
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<String> distinctTexts(List<String> values) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        return values.stream()
            .map(this::trimToNull)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
