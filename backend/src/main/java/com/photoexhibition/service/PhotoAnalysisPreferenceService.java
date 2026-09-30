package com.photoexhibition.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.entity.PhotoVisualAnalysis;
import com.photoexhibition.repository.PhotoVisualAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves remote visual-analysis fields without overwriting local model results. */
@Service
@RequiredArgsConstructor
public class PhotoAnalysisPreferenceService {
    private final PhotoVisualAnalysisRepository analysisRepository;
    private final ObjectMapper objectMapper;

    public PreferredAnalysis resolve(Long photoId) {
        if (photoId == null) return PreferredAnalysis.empty();
        return analysisRepository.findByPhotoId(photoId)
            .filter(item -> "COMPLETED".equals(item.getStatus()))
            .map(this::parse)
            .orElseGet(PreferredAnalysis::empty);
    }

    private PreferredAnalysis parse(PhotoVisualAnalysis record) {
        if (record.getAnalysisJson() == null || record.getAnalysisJson().isBlank()) {
            return PreferredAnalysis.empty();
        }
        try {
            Map<String, Object> json = objectMapper.readValue(record.getAnalysisJson(),
                new TypeReference<Map<String, Object>>() {});
            List<Object> scenes = list(json.get("scenes"));
            List<Object> moods = list(json.get("moods"));
            List<String> visualTags = strings(json.get("visualTags"));
            if (visualTags.isEmpty()) visualTags = strings(json.get("objects"));
            Map<?, ?> photography = json.get("photography") instanceof Map<?, ?>
                ? (Map<?, ?>) json.get("photography")
                : Collections.emptyMap();
            return new PreferredAnalysis(true, scenes, moods, visualTags,
                primaryLabel(scenes, "scene"), confidence(scenes),
                primaryLabel(moods, "emotion"), confidence(moods), record.getModelName(),
                score(photography.get("technicalScore")), score(photography.get("compositionScore")),
                score(photography.get("appealScore")), score(photography.get("qualityScore")));
        } catch (Exception ignored) {
            return PreferredAnalysis.empty();
        }
    }

    private List<Object> list(Object value) {
        if (!(value instanceof List<?>)) return Collections.emptyList();
        return new ArrayList<>((List<?>) value);
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?>)) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            String label = label(item, null);
            if (label != null && !label.isBlank() && !result.contains(label)) result.add(label);
        }
        return result;
    }

    private String primaryLabel(List<Object> values, String preferredKey) {
        return values.isEmpty() ? null : label(values.get(0), preferredKey);
    }

    private String label(Object value, String preferredKey) {
        if (value == null) return null;
        if (value instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) value;
            String[] keys = preferredKey == null
                ? new String[]{"label", "name", "tag", "value", "scene", "emotion", "mood"}
                : new String[]{preferredKey, "label", "name", "value", "mood"};
            for (String key : keys) {
                Object candidate = map.get(key);
                if (candidate != null && !String.valueOf(candidate).isBlank()) return String.valueOf(candidate);
            }
            return null;
        }
        return String.valueOf(value);
    }

    private Float confidence(List<Object> values) {
        if (values.isEmpty() || !(values.get(0) instanceof Map<?, ?>)) return null;
        Object raw = ((Map<?, ?>) values.get(0)).get("confidence");
        if (raw instanceof Number) return ((Number) raw).floatValue();
        try { return raw == null ? null : Float.valueOf(String.valueOf(raw)); }
        catch (NumberFormatException ignored) { return null; }
    }

    private Double score(Object raw) {
        if (raw == null) return null;
        try {
            double value = raw instanceof Number
                ? ((Number) raw).doubleValue()
                : Double.parseDouble(String.valueOf(raw));
            return Double.isFinite(value) && value >= 0.0 && value <= 100.0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static final class PreferredAnalysis {
        private final boolean completed;
        private final List<Object> scenes;
        private final List<Object> moods;
        private final List<String> visualTags;
        private final String primaryScene;
        private final Float sceneConfidence;
        private final String primaryEmotion;
        private final Float emotionConfidence;
        private final String modelName;
        private final Double technicalScore;
        private final Double compositionScore;
        private final Double appealScore;
        private final Double qualityScore;

        private PreferredAnalysis(boolean completed, List<Object> scenes, List<Object> moods,
                                  List<String> visualTags, String primaryScene, Float sceneConfidence,
                                  String primaryEmotion, Float emotionConfidence, String modelName,
                                  Double technicalScore, Double compositionScore, Double appealScore,
                                  Double qualityScore) {
            this.completed = completed;
            this.scenes = scenes;
            this.moods = moods;
            this.visualTags = visualTags;
            this.primaryScene = primaryScene;
            this.sceneConfidence = sceneConfidence;
            this.primaryEmotion = primaryEmotion;
            this.emotionConfidence = emotionConfidence;
            this.modelName = modelName;
            this.technicalScore = technicalScore;
            this.compositionScore = compositionScore;
            this.appealScore = appealScore;
            this.qualityScore = qualityScore;
        }

        public static PreferredAnalysis empty() {
            return new PreferredAnalysis(false, Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), null, null, null, null, null, null, null, null, null);
        }

        public boolean isCompleted() { return completed; }
        public boolean hasScene() { return primaryScene != null && !primaryScene.isBlank(); }
        public boolean hasEmotion() { return primaryEmotion != null && !primaryEmotion.isBlank(); }
        public boolean hasClassification() { return !visualTags.isEmpty(); }
        public boolean hasPhotographyScores() {
            return technicalScore != null || compositionScore != null || appealScore != null || qualityScore != null;
        }
        public List<Object> getScenes() { return scenes; }
        public List<Object> getMoods() { return moods; }
        public List<String> getVisualTags() { return visualTags; }
        public String getPrimaryScene() { return primaryScene; }
        public Float getSceneConfidence() { return sceneConfidence; }
        public String getPrimaryEmotion() { return primaryEmotion; }
        public Float getEmotionConfidence() { return emotionConfidence; }
        public String getModelName() { return modelName; }
        public Double getTechnicalScore() { return technicalScore; }
        public Double getCompositionScore() { return compositionScore; }
        public Double getAppealScore() { return appealScore; }
        public Double getQualityScore() { return qualityScore; }

        public Map<String, Object> sourceSummary() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("source", completed ? "AI" : "LOCAL");
            result.put("modelName", modelName);
            return result;
        }
    }
}
