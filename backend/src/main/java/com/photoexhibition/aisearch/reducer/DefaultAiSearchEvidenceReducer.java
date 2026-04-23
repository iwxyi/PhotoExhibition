package com.photoexhibition.aisearch.reducer;

import com.photoexhibition.aisearch.executor.AiSearchExecutionResult;
import com.photoexhibition.aisearch.model.AiSearchPersonAggregate;
import com.photoexhibition.aisearch.model.AiSearchPersonGrowthAggregate;
import com.photoexhibition.aisearch.model.AiSearchPersonPairAggregate;
import com.photoexhibition.aisearch.plan.AiSearchPlan;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DefaultAiSearchEvidenceReducer implements AiSearchEvidenceReducer {

    @Override
    public AiSearchEvidenceBundle reduce(AiSearchPlan plan, AiSearchExecutionResult executionResult) {
        AiSearchEvidenceBundle bundle = new AiSearchEvidenceBundle();
        bundle.setQuery(plan.getQuery());
        bundle.setQueryMode(plan.getQueryMode());
        bundle.setPlanType(plan.getPlanType());

        if ("relative_new_persons".equals(plan.getPlanType())) {
            reduceRelativeNewPersons(bundle, plan, executionResult);
            return bundle;
        }
        if ("relative_new_persons_with_technical_scope".equals(plan.getPlanType())) {
            reduceRelativeNewPersonsWithTechnicalScope(bundle, plan, executionResult);
            return bundle;
        }
        if ("relative_new_persons_with_technical_scope_then_activity".equals(plan.getPlanType())) {
            reduceRelativeNewPersonsWithTechnicalScopeThenActivity(bundle, plan, executionResult);
            return bundle;
        }
        if ("relative_new_persons_with_scoped_photos".equals(plan.getPlanType())) {
            reduceRelativeNewPersonsWithScopedPhotos(bundle, plan, executionResult);
            return bundle;
        }
        if ("relative_new_persons_with_scoped_photos_then_activity".equals(plan.getPlanType())) {
            reduceRelativeNewPersonsWithScopedPhotosThenActivity(bundle, plan, executionResult);
            return bundle;
        }
        if ("relative_new_persons_with_scoped_photos_still_active".equals(plan.getPlanType())) {
            reduceRelativeNewPersonsWithScopedPhotosStillActive(bundle, plan, executionResult);
            return bundle;
        }
        if ("relative_new_persons_then_cooccurrence".equals(plan.getPlanType())) {
            reduceRelativeNewPersonsThenCooccurrence(bundle, plan, executionResult);
            return bundle;
        }
        if ("relative_new_persons_then_cooccurrence_missing_again".equals(plan.getPlanType())) {
            reduceRelativeNewPersonsThenCooccurrenceMissingAgain(bundle, plan, executionResult);
            return bundle;
        }
        if ("relative_new_persons_then_multi_cooccurrence".equals(plan.getPlanType())) {
            reduceRelativeNewPersonsThenMultiCooccurrence(bundle, plan, executionResult);
            return bundle;
        }
        if ("relative_new_persons_then_multi_cooccurrence_missing_again".equals(plan.getPlanType())) {
            reduceRelativeNewPersonsThenMultiCooccurrenceMissingAgain(bundle, plan, executionResult);
            return bundle;
        }
        if ("relative_new_persons_then_pair_cooccurrence".equals(plan.getPlanType())) {
            reduceRelativeNewPersonsThenPairCooccurrence(bundle, plan, executionResult);
            return bundle;
        }
        if ("relative_new_persons_with_scoped_photos_then_pair_cooccurrence".equals(plan.getPlanType())) {
            reduceRelativeNewPersonsWithScopedPhotosThenPairCooccurrence(bundle, plan, executionResult);
            return bundle;
        }
        if ("relative_new_persons_body_change".equals(plan.getPlanType())) {
            reduceRelativeNewPersonsBodyChange(bundle, plan, executionResult);
            return bundle;
        }
        if ("relative_new_persons_still_active".equals(plan.getPlanType())) {
            reduceRelativeNewPersonsStillActive(bundle, plan, executionResult);
            return bundle;
        }
        if ("temporal_person_set".equals(plan.getPlanType())) {
            reduceTemporalPersonSet(bundle, plan, executionResult);
            return bundle;
        }
        if ("person_overview".equals(plan.getPlanType())) {
            reducePersonOverview(bundle, plan, executionResult);
            return bundle;
        }
        if ("person_cooccurrence".equals(plan.getPlanType())) {
            reducePersonCooccurrence(bundle, plan, executionResult);
            return bundle;
        }
        if ("person_pair_cooccurrence".equals(plan.getPlanType())) {
            reducePersonPairCooccurrence(bundle, plan, executionResult);
            return bundle;
        }
        if ("year_compare".equals(plan.getPlanType())) {
            reduceYearCompare(bundle, plan, executionResult);
            return bundle;
        }
        if ("body_change".equals(plan.getPlanType())) {
            reduceBodyChange(bundle, plan, executionResult);
            return bundle;
        }
        if ("month_overview".equals(plan.getPlanType())) {
            reduceFlatMetrics(bundle, plan, executionResult, "month_overview_metrics");
            return bundle;
        }
        if ("count_overview".equals(plan.getPlanType())) {
            reduceFlatMetrics(bundle, plan, executionResult, "count_overview_metrics");
            return bundle;
        }
        if ("theme_overview".equals(plan.getPlanType())) {
            reduceFlatMetrics(bundle, plan, executionResult, "theme_overview_metrics");
            return bundle;
        }
        if ("location_overview".equals(plan.getPlanType())) {
            reduceFlatMetrics(bundle, plan, executionResult, "location_overview_metrics");
            return bundle;
        }
        if ("album_overview".equals(plan.getPlanType())) {
            reduceFlatMetrics(bundle, plan, executionResult, "album_overview_metrics");
            return bundle;
        }
        if ("day_overview".equals(plan.getPlanType())) {
            reduceFlatMetrics(bundle, plan, executionResult, "day_overview_metrics");
            return bundle;
        }
        if ("tag_overview".equals(plan.getPlanType())) {
            reduceFlatMetrics(bundle, plan, executionResult, "tag_overview_metrics");
            return bundle;
        }

        bundle.setEvidenceStatus("limited");
        bundle.getSummary().put("planType", plan.getPlanType());
        bundle.getSummary().put("finalOutputKeys", executionResult.getFinalOutputs().keySet());
        return bundle;
    }

    private void reduceRelativeNewPersons(AiSearchEvidenceBundle bundle,
                                          AiSearchPlan plan,
                                          AiSearchExecutionResult executionResult) {
        @SuppressWarnings("unchecked")
        List<AiSearchPersonAggregate> allPersons = (List<AiSearchPersonAggregate>) executionResult.getFinalOutputs()
            .getOrDefault("sorted_new_persons", Collections.emptyList());

        bundle.setEvidenceStatus(allPersons.isEmpty() ? "none" : "sufficient");
        bundle.getSummary().put("targetYear", plan.getMetadata().get("targetYear"));
        bundle.getSummary().put("matchCount", allPersons.size());
        bundle.getSummary().put("topNames", allPersons.stream()
            .map(AiSearchPersonAggregate::getPersonName)
            .filter(name -> name != null && !name.isBlank())
            .limit(3)
            .collect(Collectors.toList()));
        bundle.getSummary().put("topMatchedPhotoCounts", allPersons.stream()
            .limit(3)
            .map(item -> Map.of(
                "personId", item.getPersonId(),
                "name", item.getPersonName(),
                "matchedPhotoCount", item.getMatchedPhotoCount()
            ))
            .collect(Collectors.toList()));
    }

    @SuppressWarnings("unchecked")
    private void reduceTemporalPersonSet(AiSearchEvidenceBundle bundle,
                                         AiSearchPlan plan,
                                         AiSearchExecutionResult executionResult) {
        List<AiSearchPersonAggregate> allPersons = (List<AiSearchPersonAggregate>) executionResult.getFinalOutputs()
            .getOrDefault("sorted_temporal_person_set", Collections.emptyList());

        bundle.setEvidenceStatus(allPersons.isEmpty() ? "none" : "sufficient");
        bundle.getSummary().put("absentYear", plan.getMetadata().get("absentYear"));
        bundle.getSummary().put("presentYear", plan.getMetadata().get("presentYear"));
        bundle.getSummary().put("missingAgainYear", plan.getMetadata().get("missingAgainYear"));
        bundle.getSummary().put("matchCount", allPersons.size());
        bundle.getSummary().put("topNames", allPersons.stream()
            .map(AiSearchPersonAggregate::getPersonName)
            .filter(name -> name != null && !name.isBlank())
            .limit(5)
            .collect(Collectors.toList()));
        bundle.getSummary().put("topMatchedPhotoCounts", allPersons.stream()
            .limit(5)
            .map(item -> Map.of(
                "personId", item.getPersonId(),
                "name", item.getPersonName(),
                "matchedPhotoCount", item.getMatchedPhotoCount()
            ))
            .collect(Collectors.toList()));
    }

    @SuppressWarnings("unchecked")
    private void reduceRelativeNewPersonsWithTechnicalScope(AiSearchEvidenceBundle bundle,
                                                            AiSearchPlan plan,
                                                            AiSearchExecutionResult executionResult) {
        List<AiSearchPersonAggregate> allPersons = (List<AiSearchPersonAggregate>) executionResult.getFinalOutputs()
            .getOrDefault("sorted_filtered_new_persons", Collections.emptyList());

        bundle.setEvidenceStatus(allPersons.isEmpty() ? "none" : "sufficient");
        bundle.getSummary().put("targetYear", plan.getMetadata().get("targetYear"));
        bundle.getSummary().put("cameraModels", plan.getMetadata().getOrDefault("cameraModels", Collections.emptyList()));
        bundle.getSummary().put("lensModels", plan.getMetadata().getOrDefault("lensModels", Collections.emptyList()));
        bundle.getSummary().put("matchCount", allPersons.size());
        bundle.getSummary().put("topNames", allPersons.stream()
            .map(AiSearchPersonAggregate::getPersonName)
            .filter(name -> name != null && !name.isBlank())
            .limit(5)
            .collect(Collectors.toList()));
        bundle.getSummary().put("topMatchedPhotoCounts", allPersons.stream()
            .limit(5)
            .map(item -> Map.of(
                "personId", item.getPersonId(),
                "name", item.getPersonName(),
                "matchedPhotoCount", item.getMatchedPhotoCount()
            ))
            .collect(Collectors.toList()));
    }

    @SuppressWarnings("unchecked")
    private void reduceRelativeNewPersonsWithTechnicalScopeThenActivity(AiSearchEvidenceBundle bundle,
                                                                        AiSearchPlan plan,
                                                                        AiSearchExecutionResult executionResult) {
        List<AiSearchPersonAggregate> allPersons = (List<AiSearchPersonAggregate>) executionResult.getFinalOutputs()
            .getOrDefault("sorted_followup_active_filtered_new_persons", Collections.emptyList());

        bundle.setEvidenceStatus(allPersons.isEmpty() ? "none" : "sufficient");
        bundle.getSummary().put("targetYear", plan.getMetadata().get("targetYear"));
        bundle.getSummary().put("cameraModels", plan.getMetadata().getOrDefault("cameraModels", Collections.emptyList()));
        bundle.getSummary().put("lensModels", plan.getMetadata().getOrDefault("lensModels", Collections.emptyList()));
        bundle.getSummary().put("matchCount", allPersons.size());
        bundle.getSummary().put("topNames", allPersons.stream()
            .map(AiSearchPersonAggregate::getPersonName)
            .filter(name -> name != null && !name.isBlank())
            .limit(5)
            .collect(Collectors.toList()));
        bundle.getSummary().put("topMatchedPhotoCounts", allPersons.stream()
            .limit(5)
            .map(item -> Map.of(
                "personId", item.getPersonId(),
                "name", item.getPersonName(),
                "matchedPhotoCount", item.getMatchedPhotoCount()
            ))
            .collect(Collectors.toList()));
    }

    @SuppressWarnings("unchecked")
    private void reduceRelativeNewPersonsThenCooccurrence(AiSearchEvidenceBundle bundle,
                                                          AiSearchPlan plan,
                                                          AiSearchExecutionResult executionResult) {
        List<AiSearchPersonAggregate> allPersons = (List<AiSearchPersonAggregate>) executionResult.getFinalOutputs()
            .getOrDefault("sorted_relative_new_persons_cooccurrence", Collections.emptyList());

        bundle.setEvidenceStatus(allPersons.isEmpty() ? "none" : "sufficient");
        bundle.getSummary().put("targetYear", plan.getMetadata().get("targetYear"));
        bundle.getSummary().put("anchorPersonId", plan.getMetadata().get("anchorPersonId"));
        bundle.getSummary().put("anchorPersonName", plan.getMetadata().get("anchorPersonName"));
        bundle.getSummary().put("matchCount", allPersons.size());
        bundle.getSummary().put("topNames", allPersons.stream()
            .map(AiSearchPersonAggregate::getPersonName)
            .filter(name -> name != null && !name.isBlank())
            .limit(5)
            .collect(Collectors.toList()));
        bundle.getSummary().put("topMatchedPhotoCounts", allPersons.stream()
            .limit(5)
            .map(item -> Map.of(
                "personId", item.getPersonId(),
                "name", item.getPersonName(),
                "matchedPhotoCount", item.getMatchedPhotoCount()
            ))
            .collect(Collectors.toList()));
    }

    @SuppressWarnings("unchecked")
    private void reduceRelativeNewPersonsThenCooccurrenceMissingAgain(AiSearchEvidenceBundle bundle,
                                                                      AiSearchPlan plan,
                                                                      AiSearchExecutionResult executionResult) {
        List<AiSearchPersonAggregate> allPersons = (List<AiSearchPersonAggregate>) executionResult.getFinalOutputs()
            .getOrDefault("sorted_cooccurring_new_persons_missing_again", Collections.emptyList());

        bundle.setEvidenceStatus(allPersons.isEmpty() ? "none" : "sufficient");
        bundle.getSummary().put("targetYear", plan.getMetadata().get("targetYear"));
        bundle.getSummary().put("missingAgainYear", plan.getMetadata().get("missingAgainYear"));
        bundle.getSummary().put("anchorPersonId", plan.getMetadata().get("anchorPersonId"));
        bundle.getSummary().put("anchorPersonName", plan.getMetadata().get("anchorPersonName"));
        bundle.getSummary().put("matchCount", allPersons.size());
        bundle.getSummary().put("topNames", allPersons.stream()
            .map(AiSearchPersonAggregate::getPersonName)
            .filter(name -> name != null && !name.isBlank())
            .limit(5)
            .collect(Collectors.toList()));
        bundle.getSummary().put("topMatchedPhotoCounts", allPersons.stream()
            .limit(5)
            .map(item -> Map.of(
                "personId", item.getPersonId(),
                "name", item.getPersonName(),
                "matchedPhotoCount", item.getMatchedPhotoCount()
            ))
            .collect(Collectors.toList()));
    }

    @SuppressWarnings("unchecked")
    private void reduceRelativeNewPersonsThenMultiCooccurrence(AiSearchEvidenceBundle bundle,
                                                               AiSearchPlan plan,
                                                               AiSearchExecutionResult executionResult) {
        List<AiSearchPersonAggregate> allPersons = (List<AiSearchPersonAggregate>) executionResult.getFinalOutputs()
            .getOrDefault("sorted_relative_new_persons_multi_cooccurrence", Collections.emptyList());

        bundle.setEvidenceStatus(allPersons.isEmpty() ? "none" : "sufficient");
        bundle.getSummary().put("targetYear", plan.getMetadata().get("targetYear"));
        bundle.getSummary().put("anchorPersonIds", plan.getMetadata().getOrDefault("anchorPersonIds", Collections.emptyList()));
        bundle.getSummary().put("anchorPersonNames", plan.getMetadata().getOrDefault("anchorPersonNames", Collections.emptyList()));
        bundle.getSummary().put("matchCount", allPersons.size());
        bundle.getSummary().put("topNames", allPersons.stream()
            .map(AiSearchPersonAggregate::getPersonName)
            .filter(name -> name != null && !name.isBlank())
            .limit(5)
            .collect(Collectors.toList()));
        bundle.getSummary().put("topMatchedPhotoCounts", allPersons.stream()
            .limit(5)
            .map(item -> Map.of(
                "personId", item.getPersonId(),
                "name", item.getPersonName(),
                "matchedPhotoCount", item.getMatchedPhotoCount()
            ))
            .collect(Collectors.toList()));
    }

    @SuppressWarnings("unchecked")
    private void reduceRelativeNewPersonsThenMultiCooccurrenceMissingAgain(AiSearchEvidenceBundle bundle,
                                                                           AiSearchPlan plan,
                                                                           AiSearchExecutionResult executionResult) {
        List<AiSearchPersonAggregate> allPersons = (List<AiSearchPersonAggregate>) executionResult.getFinalOutputs()
            .getOrDefault("sorted_multi_cooccurring_new_persons_missing_again", Collections.emptyList());

        bundle.setEvidenceStatus(allPersons.isEmpty() ? "none" : "sufficient");
        bundle.getSummary().put("targetYear", plan.getMetadata().get("targetYear"));
        bundle.getSummary().put("missingAgainYear", plan.getMetadata().get("missingAgainYear"));
        bundle.getSummary().put("anchorPersonIds", plan.getMetadata().getOrDefault("anchorPersonIds", Collections.emptyList()));
        bundle.getSummary().put("anchorPersonNames", plan.getMetadata().getOrDefault("anchorPersonNames", Collections.emptyList()));
        bundle.getSummary().put("matchCount", allPersons.size());
        bundle.getSummary().put("topNames", allPersons.stream()
            .map(AiSearchPersonAggregate::getPersonName)
            .filter(name -> name != null && !name.isBlank())
            .limit(5)
            .collect(Collectors.toList()));
        bundle.getSummary().put("topMatchedPhotoCounts", allPersons.stream()
            .limit(5)
            .map(item -> Map.of(
                "personId", item.getPersonId(),
                "name", item.getPersonName(),
                "matchedPhotoCount", item.getMatchedPhotoCount()
            ))
            .collect(Collectors.toList()));
    }

    @SuppressWarnings("unchecked")
    private void reduceRelativeNewPersonsThenPairCooccurrence(AiSearchEvidenceBundle bundle,
                                                              AiSearchPlan plan,
                                                              AiSearchExecutionResult executionResult) {
        List<AiSearchPersonPairAggregate> allPairs = (List<AiSearchPersonPairAggregate>) executionResult.getFinalOutputs()
            .getOrDefault("sorted_relative_new_person_pairs", Collections.emptyList());

        bundle.setEvidenceStatus(allPairs.isEmpty() ? "none" : "sufficient");
        bundle.getSummary().put("targetYear", plan.getMetadata().get("targetYear"));
        bundle.getSummary().put("matchCount", allPairs.size());
        bundle.getSummary().put("topPairs", allPairs.stream()
            .limit(5)
            .map(item -> Map.of(
                "personAName", item.getPersonAName(),
                "personBName", item.getPersonBName(),
                "matchedPhotoCount", item.getMatchedPhotoCount()
            ))
            .collect(Collectors.toList()));
        bundle.getSummary().put("topNames", allPairs.stream()
            .limit(3)
            .map(item -> item.getPersonAName() + " / " + item.getPersonBName())
            .collect(Collectors.toList()));
    }

    @SuppressWarnings("unchecked")
    private void reduceRelativeNewPersonsBodyChange(AiSearchEvidenceBundle bundle,
                                                    AiSearchPlan plan,
                                                    AiSearchExecutionResult executionResult) {
        List<AiSearchPersonGrowthAggregate> allPersons = (List<AiSearchPersonGrowthAggregate>) executionResult.getFinalOutputs()
            .getOrDefault("sorted_body_change_new_persons", Collections.emptyList());

        bundle.setEvidenceStatus(allPersons.isEmpty() ? "none" : "sufficient");
        bundle.getSummary().put("targetYear", plan.getMetadata().get("targetYear"));
        bundle.getSummary().put("startYear", plan.getMetadata().get("startYear"));
        bundle.getSummary().put("endYear", plan.getMetadata().get("endYear"));
        bundle.getSummary().put("matchCount", allPersons.size());
        bundle.getSummary().put("topNames", allPersons.stream()
            .map(AiSearchPersonGrowthAggregate::getPersonName)
            .filter(name -> name != null && !name.isBlank())
            .limit(5)
            .collect(Collectors.toList()));
        bundle.getSummary().put("topChangePercents", allPersons.stream()
            .limit(5)
            .map(item -> Map.of(
                "personId", item.getPersonId(),
                "name", item.getPersonName(),
                "changePercent", item.getChangePercent() == null ? 0D : item.getChangePercent()
            ))
            .collect(Collectors.toList()));
    }

    @SuppressWarnings("unchecked")
    private void reduceRelativeNewPersonsWithScopedPhotosThenPairCooccurrence(AiSearchEvidenceBundle bundle,
                                                                              AiSearchPlan plan,
                                                                              AiSearchExecutionResult executionResult) {
        List<AiSearchPersonPairAggregate> allPairs = (List<AiSearchPersonPairAggregate>) executionResult.getFinalOutputs()
            .getOrDefault("sorted_relative_scoped_new_person_pairs", Collections.emptyList());

        bundle.setEvidenceStatus(allPairs.isEmpty() ? "none" : "sufficient");
        bundle.getSummary().put("targetYear", plan.getMetadata().get("targetYear"));
        bundle.getSummary().put("cameraModels", plan.getMetadata().getOrDefault("cameraModels", Collections.emptyList()));
        bundle.getSummary().put("lensModels", plan.getMetadata().getOrDefault("lensModels", Collections.emptyList()));
        bundle.getSummary().put("scopeKeywords", plan.getMetadata().getOrDefault("scopeKeywords", Collections.emptyList()));
        bundle.getSummary().put("matchCount", allPairs.size());
        bundle.getSummary().put("topPairs", allPairs.stream()
            .limit(5)
            .map(item -> Map.of(
                "personAName", item.getPersonAName(),
                "personBName", item.getPersonBName(),
                "matchedPhotoCount", item.getMatchedPhotoCount()
            ))
            .collect(Collectors.toList()));
        bundle.getSummary().put("topNames", allPairs.stream()
            .limit(3)
            .map(item -> item.getPersonAName() + " / " + item.getPersonBName())
            .collect(Collectors.toList()));
    }

    @SuppressWarnings("unchecked")
    private void reduceRelativeNewPersonsWithScopedPhotos(AiSearchEvidenceBundle bundle,
                                                          AiSearchPlan plan,
                                                          AiSearchExecutionResult executionResult) {
        List<AiSearchPersonAggregate> allPersons = (List<AiSearchPersonAggregate>) executionResult.getFinalOutputs()
            .getOrDefault("sorted_scoped_new_persons", Collections.emptyList());

        bundle.setEvidenceStatus(allPersons.isEmpty() ? "none" : "sufficient");
        bundle.getSummary().put("targetYear", plan.getMetadata().get("targetYear"));
        bundle.getSummary().put("cameraModels", plan.getMetadata().getOrDefault("cameraModels", Collections.emptyList()));
        bundle.getSummary().put("lensModels", plan.getMetadata().getOrDefault("lensModels", Collections.emptyList()));
        bundle.getSummary().put("scopeKeywords", plan.getMetadata().getOrDefault("scopeKeywords", Collections.emptyList()));
        bundle.getSummary().put("matchCount", allPersons.size());
        bundle.getSummary().put("topNames", allPersons.stream()
            .map(AiSearchPersonAggregate::getPersonName)
            .filter(name -> name != null && !name.isBlank())
            .limit(5)
            .collect(Collectors.toList()));
    }

    @SuppressWarnings("unchecked")
    private void reduceRelativeNewPersonsWithScopedPhotosThenActivity(AiSearchEvidenceBundle bundle,
                                                                      AiSearchPlan plan,
                                                                      AiSearchExecutionResult executionResult) {
        List<AiSearchPersonAggregate> allPersons = (List<AiSearchPersonAggregate>) executionResult.getFinalOutputs()
            .getOrDefault("sorted_followup_active_scoped_new_persons", Collections.emptyList());

        bundle.setEvidenceStatus(allPersons.isEmpty() ? "none" : "sufficient");
        bundle.getSummary().put("targetYear", plan.getMetadata().get("targetYear"));
        bundle.getSummary().put("cameraModels", plan.getMetadata().getOrDefault("cameraModels", Collections.emptyList()));
        bundle.getSummary().put("lensModels", plan.getMetadata().getOrDefault("lensModels", Collections.emptyList()));
        bundle.getSummary().put("scopeKeywords", plan.getMetadata().getOrDefault("scopeKeywords", Collections.emptyList()));
        bundle.getSummary().put("matchCount", allPersons.size());
        bundle.getSummary().put("topNames", allPersons.stream()
            .map(AiSearchPersonAggregate::getPersonName)
            .filter(name -> name != null && !name.isBlank())
            .limit(5)
            .collect(Collectors.toList()));
        bundle.getSummary().put("topMatchedPhotoCounts", allPersons.stream()
            .limit(5)
            .map(item -> Map.of(
                "personId", item.getPersonId(),
                "name", item.getPersonName(),
                "matchedPhotoCount", item.getMatchedPhotoCount()
            ))
            .collect(Collectors.toList()));
    }

    @SuppressWarnings("unchecked")
    private void reduceRelativeNewPersonsWithScopedPhotosStillActive(AiSearchEvidenceBundle bundle,
                                                                     AiSearchPlan plan,
                                                                     AiSearchExecutionResult executionResult) {
        List<AiSearchPersonAggregate> allPersons = (List<AiSearchPersonAggregate>) executionResult.getFinalOutputs()
            .getOrDefault("sorted_still_active_scoped_new_persons", Collections.emptyList());

        bundle.setEvidenceStatus(allPersons.isEmpty() ? "none" : "sufficient");
        bundle.getSummary().put("targetYear", plan.getMetadata().get("targetYear"));
        bundle.getSummary().put("activeYear", plan.getMetadata().get("activeYear"));
        bundle.getSummary().put("cameraModels", plan.getMetadata().getOrDefault("cameraModels", Collections.emptyList()));
        bundle.getSummary().put("lensModels", plan.getMetadata().getOrDefault("lensModels", Collections.emptyList()));
        bundle.getSummary().put("scopeKeywords", plan.getMetadata().getOrDefault("scopeKeywords", Collections.emptyList()));
        bundle.getSummary().put("matchCount", allPersons.size());
        bundle.getSummary().put("topNames", allPersons.stream()
            .map(AiSearchPersonAggregate::getPersonName)
            .filter(name -> name != null && !name.isBlank())
            .limit(5)
            .collect(Collectors.toList()));
    }

    @SuppressWarnings("unchecked")
    private void reduceRelativeNewPersonsStillActive(AiSearchEvidenceBundle bundle,
                                                     AiSearchPlan plan,
                                                     AiSearchExecutionResult executionResult) {
        List<AiSearchPersonAggregate> allPersons = (List<AiSearchPersonAggregate>) executionResult.getFinalOutputs()
            .getOrDefault("sorted_still_active_new_persons", Collections.emptyList());

        bundle.setEvidenceStatus(allPersons.isEmpty() ? "none" : "sufficient");
        bundle.getSummary().put("targetYear", plan.getMetadata().get("targetYear"));
        bundle.getSummary().put("activeYear", plan.getMetadata().get("activeYear"));
        bundle.getSummary().put("matchCount", allPersons.size());
        bundle.getSummary().put("topNames", allPersons.stream()
            .map(AiSearchPersonAggregate::getPersonName)
            .filter(name -> name != null && !name.isBlank())
            .limit(5)
            .collect(Collectors.toList()));
        bundle.getSummary().put("topMatchedPhotoCounts", allPersons.stream()
            .limit(5)
            .map(item -> Map.of(
                "personId", item.getPersonId(),
                "name", item.getPersonName(),
                "matchedPhotoCount", item.getMatchedPhotoCount()
            ))
            .collect(Collectors.toList()));
    }

    @SuppressWarnings("unchecked")
    private void reducePersonOverview(AiSearchEvidenceBundle bundle,
                                      AiSearchPlan plan,
                                      AiSearchExecutionResult executionResult) {
        List<AiSearchPersonAggregate> allPersons = (List<AiSearchPersonAggregate>) executionResult.getFinalOutputs()
            .getOrDefault("sorted_persons", Collections.emptyList());

        bundle.setEvidenceStatus(allPersons.isEmpty() ? "none" : "sufficient");
        bundle.getSummary().put("matchCount", allPersons.size());
        bundle.getSummary().put("topNames", allPersons.stream()
            .map(AiSearchPersonAggregate::getPersonName)
            .filter(name -> name != null && !name.isBlank())
            .limit(5)
            .collect(Collectors.toList()));
        bundle.getSummary().put("topMatchedPhotoCounts", allPersons.stream()
            .limit(5)
            .map(item -> Map.of(
                "personId", item.getPersonId(),
                "name", item.getPersonName(),
                "matchedPhotoCount", item.getMatchedPhotoCount()
            ))
            .collect(Collectors.toList()));
        bundle.getSummary().put("planType", plan.getPlanType());
    }

    @SuppressWarnings("unchecked")
    private void reducePersonCooccurrence(AiSearchEvidenceBundle bundle,
                                          AiSearchPlan plan,
                                          AiSearchExecutionResult executionResult) {
        List<AiSearchPersonAggregate> allPersons = (List<AiSearchPersonAggregate>) executionResult.getFinalOutputs()
            .getOrDefault("sorted_cooccurring_persons", Collections.emptyList());

        bundle.setEvidenceStatus(allPersons.isEmpty() ? "none" : "sufficient");
        bundle.getSummary().put("anchorPersonId", plan.getMetadata().get("anchorPersonId"));
        bundle.getSummary().put("anchorPersonName", plan.getMetadata().get("anchorPersonName"));
        bundle.getSummary().put("matchCount", allPersons.size());
        bundle.getSummary().put("topNames", allPersons.stream()
            .map(AiSearchPersonAggregate::getPersonName)
            .filter(name -> name != null && !name.isBlank())
            .limit(5)
            .collect(Collectors.toList()));
        bundle.getSummary().put("topMatchedPhotoCounts", allPersons.stream()
            .limit(5)
            .map(item -> Map.of(
                "personId", item.getPersonId(),
                "name", item.getPersonName(),
                "matchedPhotoCount", item.getMatchedPhotoCount()
            ))
            .collect(Collectors.toList()));
        bundle.getSummary().put("planType", plan.getPlanType());
    }

    @SuppressWarnings("unchecked")
    private void reducePersonPairCooccurrence(AiSearchEvidenceBundle bundle,
                                              AiSearchPlan plan,
                                              AiSearchExecutionResult executionResult) {
        List<AiSearchPersonPairAggregate> allPairs = (List<AiSearchPersonPairAggregate>) executionResult.getFinalOutputs()
            .getOrDefault("sorted_cooccurring_pairs", Collections.emptyList());

        bundle.setEvidenceStatus(allPairs.isEmpty() ? "none" : "sufficient");
        bundle.getSummary().put("matchCount", allPairs.size());
        bundle.getSummary().put("topPairs", allPairs.stream()
            .limit(5)
            .map(item -> Map.of(
                "personAName", item.getPersonAName(),
                "personBName", item.getPersonBName(),
                "matchedPhotoCount", item.getMatchedPhotoCount()
            ))
            .collect(Collectors.toList()));
        bundle.getSummary().put("topNames", allPairs.stream()
            .limit(3)
            .map(item -> item.getPersonAName() + " / " + item.getPersonBName())
            .collect(Collectors.toList()));
        bundle.getSummary().put("planType", plan.getPlanType());
    }

    @SuppressWarnings("unchecked")
    private void reduceYearCompare(AiSearchEvidenceBundle bundle,
                                   AiSearchPlan plan,
                                   AiSearchExecutionResult executionResult) {
        Map<String, Object> metrics = (Map<String, Object>) executionResult.getFinalOutputs()
            .getOrDefault("year_compare_metrics", Collections.emptyMap());
        long leftCount = asLong(metrics.get("leftCount"));
        long rightCount = asLong(metrics.get("rightCount"));

        bundle.setEvidenceStatus((leftCount == 0 && rightCount == 0) ? "none" : "sufficient");
        bundle.getSummary().putAll(metrics);
    }

    private long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    @SuppressWarnings("unchecked")
    private void reduceBodyChange(AiSearchEvidenceBundle bundle,
                                  AiSearchPlan plan,
                                  AiSearchExecutionResult executionResult) {
        Map<String, Object> metrics = (Map<String, Object>) executionResult.getFinalOutputs()
            .getOrDefault("body_change_metrics", Collections.emptyMap());
        String trend = String.valueOf(metrics.getOrDefault("trend", "unknown"));
        int totalPhotos = (int) asLong(metrics.get("totalPhotos"));

        String evidenceStatus;
        if ("error".equals(trend)) {
            evidenceStatus = "limited";
        } else if (totalPhotos <= 0 || "unknown".equals(trend) || "insufficient_data".equals(trend)) {
            evidenceStatus = "none";
        } else {
            evidenceStatus = "sufficient";
        }
        bundle.setEvidenceStatus(evidenceStatus);
        bundle.getSummary().putAll(metrics);
    }

    @SuppressWarnings("unchecked")
    private void reduceFlatMetrics(AiSearchEvidenceBundle bundle,
                                   AiSearchPlan plan,
                                   AiSearchExecutionResult executionResult,
                                   String key) {
        Map<String, Object> metrics = (Map<String, Object>) executionResult.getFinalOutputs()
            .getOrDefault(key, Collections.emptyMap());
        long totalMatched = asLong(metrics.get("totalMatched"));
        bundle.setEvidenceStatus(totalMatched > 0 ? "sufficient" : "none");
        bundle.getSummary().putAll(metrics);
        bundle.getSummary().put("planType", plan.getPlanType());
    }
}
