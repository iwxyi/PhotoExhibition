package com.photoexhibition.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class PhotoDTO {
    private Long id;
    private Long albumId;
    private String filename;
    private String originalPath;
    private String thumbnailPath;
    private String webpPath;
    private String smallThumbPath;
    private String mediumThumbPath;
    private String largeThumbPath;
    private Long fileSize;
    private String contentHash;
    private Long canonicalPhotoId;
    private boolean canonicalSource;
    private boolean duplicateContent;
    private Integer width;
    private Integer height;
    private String format;
    private String dominantColor;
    private String colorCategory;
    private List<String> colorPalette;
    private Map<String, Object> exifData;
    private String cameraMake;
    private String cameraModel;
    private String lensModel;
    private String focalLength;
    private Double focalLengthMm;
    private String aperture;
    private Double apertureValue;
    private String shutterSpeed;
    private Double shutterSpeedSeconds;
    private Integer iso;
    private LocalDateTime takenAt;
    private Double qualityScore;
    private Double focusX; // 焦点X位置（百分比 0-100）
    private Double focusY; // 焦点Y位置（百分比 0-100）
    private Integer viewCount;
    private Integer likeCount;
    private Boolean isFeatured;
    private Boolean isHidden;
    private Boolean isPinned;

    // AI评分相关字段
    private Double aiOverallScore; // AI综合评分
    private Double aiTechnicalScore; // AI技术评分
    private Double aiCompositionScore; // AI构图评分
    private Double aiAppealScore; // AI吸引力评分
    private String scoreSource; // AI / MIXED / LOCAL
    private String technicalScoreSource;
    private String compositionScoreSource;
    private String appealScoreSource;
    private Double localOverallScore;
    private Double localTechnicalScore;
    private Double localCompositionScore;
    private Double localAppealScore;
    private Double visualAiQualityScore;
    private Double visualAiTechnicalScore;
    private Double visualAiCompositionScore;
    private Double visualAiAppealScore;
    private List<String> aiStrengths; // AI分析优点
    private List<String> aiWeaknesses; // AI分析不足
    private List<String> aiSuggestions; // AI改进建议
    // 有效分析结果：AI大模型有结果时优先，否则回退本地结果
    private List<Object> sceneAnalysis;
    private List<Object> emotionAnalysis;
    private String primaryScene;
    private String primaryEmotion;
    private Float sceneConfidence;
    private Float emotionConfidence;
    private String sceneAnalysisSource;
    private String emotionAnalysisSource;
    private String classificationSource;
    // 分源结果，便于管理端对比与排障
    private List<Object> localSceneAnalysis;
    private List<Object> localEmotionAnalysis;
    private List<Map<String, Object>> localClassificationAnalysis;
    private List<Object> aiSceneAnalysis;
    private List<Object> aiEmotionAnalysis;
    private List<String> aiVisualTags;
    private String aiVisualModel;
    private List<TagDTO> tags;
    // 如果图片被指派给某个人物（非人脸关联），在此记录
    private Long assignedPersonId;
    private String assignedPersonName;
    private List<FaceDTO> faces;
    private LocalDateTime createdAt;
}
