package com.photoexhibition.aisearch.planner;

import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.dto.AiSearchAnalysisOperation;
import com.photoexhibition.dto.AiSearchAnalysisScope;
import com.photoexhibition.dto.AiSearchAnalysisSpec;
import com.photoexhibition.dto.AiSearchAnalysisSubject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AiSearchAnalysisSpecMapper {

    private final RelativeNewPersonsAiSearchPlanner relativeNewPersonsAiSearchPlanner;
    private final RelativeNewPersonsStillActiveAiSearchPlanner relativeNewPersonsStillActiveAiSearchPlanner;
    private final RelativeNewPersonsBodyChangeAiSearchPlanner relativeNewPersonsBodyChangeAiSearchPlanner;
    private final RelativeNewPersonsThenCooccurrenceAiSearchPlanner relativeNewPersonsThenCooccurrenceAiSearchPlanner;
    private final RelativeNewPersonsThenCooccurrenceMissingAgainAiSearchPlanner relativeNewPersonsThenCooccurrenceMissingAgainAiSearchPlanner;
    private final RelativeNewPersonsThenMultiCooccurrenceAiSearchPlanner relativeNewPersonsThenMultiCooccurrenceAiSearchPlanner;
    private final RelativeNewPersonsThenMultiCooccurrenceMissingAgainAiSearchPlanner relativeNewPersonsThenMultiCooccurrenceMissingAgainAiSearchPlanner;
    private final RelativeNewPersonsThenPairCooccurrenceAiSearchPlanner relativeNewPersonsThenPairCooccurrenceAiSearchPlanner;
    private final RelativeNewPersonsWithScopedPhotosAiSearchPlanner relativeNewPersonsWithScopedPhotosAiSearchPlanner;
    private final RelativeNewPersonsWithScopedPhotosThenActivityAiSearchPlanner relativeNewPersonsWithScopedPhotosThenActivityAiSearchPlanner;
    private final RelativeNewPersonsWithScopedPhotosStillActiveAiSearchPlanner relativeNewPersonsWithScopedPhotosStillActiveAiSearchPlanner;
    private final RelativeNewPersonsWithTechnicalScopeAiSearchPlanner relativeNewPersonsWithTechnicalScopeAiSearchPlanner;
    private final RelativeNewPersonsWithTechnicalScopeThenActivityAiSearchPlanner relativeNewPersonsWithTechnicalScopeThenActivityAiSearchPlanner;
    private final TemporalPersonSetAiSearchPlanner temporalPersonSetAiSearchPlanner;
    private final CountOverviewAiSearchPlanner countOverviewAiSearchPlanner;
    private final PersonOverviewAiSearchPlanner personOverviewAiSearchPlanner;
    private final PersonCooccurrenceAiSearchPlanner personCooccurrenceAiSearchPlanner;
    private final PersonPairCooccurrenceAiSearchPlanner personPairCooccurrenceAiSearchPlanner;
    private final AlbumOverviewAiSearchPlanner albumOverviewAiSearchPlanner;
    private final MonthOverviewAiSearchPlanner monthOverviewAiSearchPlanner;
    private final LocationOverviewAiSearchPlanner locationOverviewAiSearchPlanner;
    private final DayOverviewAiSearchPlanner dayOverviewAiSearchPlanner;
    private final TagOverviewAiSearchPlanner tagOverviewAiSearchPlanner;
    private final ThemeOverviewAiSearchPlanner themeOverviewAiSearchPlanner;
    private final YearCompareAiSearchPlanner yearCompareAiSearchPlanner;

    public AiSearchPlan map(String query, AiSearchAnalysisSpec spec, int offset, int limit) {
        if (spec == null || spec.getSubject() == null || spec.getOperation() == null) {
            return null;
        }

        String subjectType = normalize(spec.getSubject().getType());
        String operationType = normalize(spec.getOperation().getType());
        String scopeType = normalize(spec.getScope() == null ? null : spec.getScope().getType());

        if ("filtered_scope".equals(subjectType)) {
            return mapFilteredScope(query, operationType, spec.getOperation(), offset, limit);
        }

        if ("relative_new_persons".equals(subjectType)) {
            return mapRelativeNewPersons(query, spec.getSubject(), spec.getOperation(), spec.getScope(), scopeType, offset, limit);
        }
        if ("temporal_person_set".equals(subjectType) && "identity".equals(operationType)) {
            Integer absentYear = spec.getSubject().getAbsentYear();
            Integer presentYear = spec.getSubject().getPresentYear();
            Integer missingAgainYear = spec.getSubject().getMissingAgainYear();
            if (absentYear == null || presentYear == null || missingAgainYear == null) {
                return null;
            }
            return temporalPersonSetAiSearchPlanner.planForYears(query, absentYear, presentYear, missingAgainYear, offset, limit);
        }

        return null;
    }

    private AiSearchPlan mapFilteredScope(String query,
                                          String operationType,
                                          AiSearchAnalysisOperation operation,
                                          int offset,
                                          int limit) {
        if ("count_overview".equals(operationType)) {
            return countOverviewAiSearchPlanner.plan(query);
        }
        if ("person_overview".equals(operationType)) {
            return personOverviewAiSearchPlanner.plan(query, offset, limit);
        }
        if ("person_pair_cooccurrence".equals(operationType)) {
            return personPairCooccurrenceAiSearchPlanner.plan(query, offset, limit);
        }
        if ("album_overview".equals(operationType)) {
            return albumOverviewAiSearchPlanner.plan(query);
        }
        if ("month_overview".equals(operationType)) {
            return monthOverviewAiSearchPlanner.plan(query);
        }
        if ("location_overview".equals(operationType)) {
            return locationOverviewAiSearchPlanner.plan(query);
        }
        if ("day_overview".equals(operationType)) {
            return dayOverviewAiSearchPlanner.plan(query);
        }
        if ("tag_overview".equals(operationType)) {
            return tagOverviewAiSearchPlanner.plan(query);
        }
        if ("theme_overview".equals(operationType)) {
            return themeOverviewAiSearchPlanner.plan(query);
        }
        if ("person_cooccurrence".equals(operationType)) {
            List<Long> anchorPersonIds = cleanLongs(operation.getAnchorPersonIds());
            if (anchorPersonIds.isEmpty()) {
                return null;
            }
            return personCooccurrenceAiSearchPlanner.plan(query, anchorPersonIds.get(0), offset, limit);
        }
        if ("year_compare".equals(operationType)) {
            Integer leftYear = operation.getLeftYear();
            Integer rightYear = operation.getRightYear();
            if (leftYear == null || rightYear == null) {
                return null;
            }
            return yearCompareAiSearchPlanner.plan(query, leftYear, rightYear, operation.getSubject());
        }
        return null;
    }

    private AiSearchPlan mapRelativeNewPersons(String query,
                                               AiSearchAnalysisSubject subject,
                                               AiSearchAnalysisOperation operation,
                                               AiSearchAnalysisScope scope,
                                               String scopeType,
                                               int offset,
                                               int limit) {
        Integer targetYear = subject.getTargetYear();
        if (targetYear == null) {
            return null;
        }

        List<String> cameraModels = cleanStrings(scope == null ? null : scope.getCameraModels());
        List<String> lensModels = cleanStrings(scope == null ? null : scope.getLensModels());
        List<String> scopeKeywords = cleanStrings(scope == null ? null : scope.getScopeKeywords());
        List<Long> anchorPersonIds = cleanLongs(operation.getAnchorPersonIds());
        List<String> anchorPersonNames = cleanStrings(operation.getAnchorPersonNames());
        String operationType = normalize(operation.getType());
        String desiredTrend = normalize(operation.getDesiredTrend());

        if ("identity".equals(operationType)) {
            if ("scoped_photos".equals(scopeType) && !scopeKeywords.isEmpty()) {
                return relativeNewPersonsWithScopedPhotosAiSearchPlanner.planForScope(
                    query, targetYear, cameraModels, lensModels, scopeKeywords, offset, limit
                );
            }
            if ("technical".equals(scopeType) && (!cameraModels.isEmpty() || !lensModels.isEmpty())) {
                return relativeNewPersonsWithTechnicalScopeAiSearchPlanner.planForScope(
                    query, targetYear, cameraModels, lensModels, offset, limit
                );
            }
            return relativeNewPersonsAiSearchPlanner.planForYears(query, targetYear, offset, limit);
        }

        if ("still_active".equals(operationType)) {
            Integer activeYear = operation.getActiveYear();
            if (activeYear == null) {
                return null;
            }
            if ("scoped_photos".equals(scopeType) && !scopeKeywords.isEmpty()) {
                return relativeNewPersonsWithScopedPhotosStillActiveAiSearchPlanner.planForScope(
                    query, targetYear, activeYear, cameraModels, lensModels, scopeKeywords, offset, limit
                );
            }
            return relativeNewPersonsStillActiveAiSearchPlanner.planForYears(query, targetYear, activeYear, offset, limit);
        }

        if ("activity_rank".equals(operationType)) {
            if ("scoped_photos".equals(scopeType) && !scopeKeywords.isEmpty()) {
                return relativeNewPersonsWithScopedPhotosThenActivityAiSearchPlanner.planForScope(
                    query, targetYear, cameraModels, lensModels, scopeKeywords, offset, limit
                );
            }
            if ("technical".equals(scopeType) && (!cameraModels.isEmpty() || !lensModels.isEmpty())) {
                return relativeNewPersonsWithTechnicalScopeThenActivityAiSearchPlanner.planForScope(
                    query, targetYear, cameraModels, lensModels, offset, limit
                );
            }
            return null;
        }

        if ("body_change".equals(operationType)) {
            Integer startYear = operation.getStartYear();
            Integer endYear = operation.getEndYear();
            if (startYear == null || endYear == null) {
                return null;
            }
            if (desiredTrend != null && !"gained_weight".equals(desiredTrend)) {
                return null;
            }
            return relativeNewPersonsBodyChangeAiSearchPlanner.planForYears(query, targetYear, startYear, endYear, offset, limit);
        }

        if ("cooccurrence".equals(operationType)) {
            if (anchorPersonIds.isEmpty()) {
                return null;
            }
            Integer missingAgainYear = operation.getMissingAgainYear();
            if (anchorPersonIds.size() == 1) {
                String anchorName = anchorPersonNames.isEmpty() ? null : anchorPersonNames.get(0);
                if (missingAgainYear != null) {
                    return relativeNewPersonsThenCooccurrenceMissingAgainAiSearchPlanner.planForYears(
                        query, targetYear, missingAgainYear, anchorPersonIds.get(0), anchorName, offset, limit
                    );
                }
                return relativeNewPersonsThenCooccurrenceAiSearchPlanner.planForYears(
                    query, targetYear, anchorPersonIds.get(0), anchorName, offset, limit
                );
            }
            if (missingAgainYear != null) {
                return relativeNewPersonsThenMultiCooccurrenceMissingAgainAiSearchPlanner.planForYears(
                    query, targetYear, missingAgainYear, anchorPersonIds, anchorPersonNames, offset, limit
                );
            }
            return relativeNewPersonsThenMultiCooccurrenceAiSearchPlanner.planForYears(
                query, targetYear, anchorPersonIds, anchorPersonNames, offset, limit
            );
        }

        if ("pair_cooccurrence".equals(operationType)) {
            return relativeNewPersonsThenPairCooccurrenceAiSearchPlanner.planForYear(query, targetYear, offset, limit);
        }

        return null;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private List<String> cleanStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(item -> !item.isEmpty())
            .distinct()
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<Long> cleanLongs(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream()
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toCollection(ArrayList::new));
    }
}
