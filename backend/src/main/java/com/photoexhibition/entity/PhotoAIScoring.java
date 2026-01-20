package com.photoexhibition.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI图片评分实体
 * 存储AI对图片的综合评分和详细分析结果
 */
@Entity
@Table(name = "photo_ai_scoring")
@Data
public class PhotoAIScoring {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "photo_id", nullable = false)
    private Long photoId;

    // 综合评分
    @Column(name = "overall_score", precision = 5, scale = 2, nullable = false)
    private Double overallScore;

    @Column(name = "score_version", length = 20)
    private String scoreVersion = "1.0";

    // 分维度评分
    @Column(name = "technical_score", precision = 5, scale = 2)
    private Double technicalScore;

    @Column(name = "composition_score", precision = 5, scale = 2)
    private Double compositionScore;

    @Column(name = "appeal_score", precision = 5, scale = 2)
    private Double appealScore;

    // 评分权重
    @Column(name = "technical_weight", precision = 3, scale = 2)
    private Double technicalWeight = 0.40;

    @Column(name = "composition_weight", precision = 3, scale = 2)
    private Double compositionWeight = 0.35;

    @Column(name = "appeal_weight", precision = 3, scale = 2)
    private Double appealWeight = 0.25;

    // 详细分析结果 (JSON格式存储)
    @Column(name = "technical_analysis", columnDefinition = "JSON")
    private String technicalAnalysis;

    @Column(name = "composition_analysis", columnDefinition = "JSON")
    private String compositionAnalysis;

    @Column(name = "appeal_analysis", columnDefinition = "JSON")
    private String appealAnalysis;

    // 优点和不足 (JSON数组格式)
    @Column(name = "strengths", columnDefinition = "JSON")
    private String strengths;

    @Column(name = "weaknesses", columnDefinition = "JSON")
    private String weaknesses;

    // 改进建议
    @Column(name = "improvement_suggestions", columnDefinition = "JSON")
    private String improvementSuggestions;

    // AI模型信息
    @Column(name = "models_used", columnDefinition = "JSON")
    private String modelsUsed;

    @Column(name = "processing_time_ms")
    private Integer processingTimeMs;

    // 评分状态
    @Enumerated(EnumType.STRING)
    @Column(name = "scoring_status", length = 20)
    private AIScoringStatus scoringStatus = AIScoringStatus.COMPLETED;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // 时间戳
    @Column(name = "scored_at", nullable = false)
    private LocalDateTime scoredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 关联的照片（非级联）
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_id", insertable = false, updatable = false)
    @JsonIgnore
    private Photo photo;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (scoredAt == null) {
            scoredAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 获取或创建评分状态
     */
    public AIScoringStatus getScoringStatus() {
        return scoringStatus != null ? scoringStatus : AIScoringStatus.PENDING;
    }

    /**
     * 设置评分状态
     */
    public void setScoringStatus(AIScoringStatus scoringStatus) {
        this.scoringStatus = scoringStatus;
    }

    /**
     * 计算综合评分
     */
    public void calculateOverallScore() {
        double calculatedScore = 0.0;
        double totalWeight = 0.0;

        // 使用可用评分计算加权平均值
        if (technicalScore != null && technicalWeight != null) {
            calculatedScore += (technicalScore * technicalWeight);
            totalWeight += technicalWeight;
        }
        if (compositionScore != null && compositionWeight != null) {
            calculatedScore += (compositionScore * compositionWeight);
            totalWeight += compositionWeight;
        }
        if (appealScore != null && appealWeight != null) {
            calculatedScore += (appealScore * appealWeight);
            totalWeight += appealWeight;
        }

        // 如果有至少一个有效评分，使用加权平均值，否则使用默认值
        if (totalWeight > 0) {
            this.overallScore = calculatedScore / totalWeight; // 直接使用加权平均值，不归一化
        } else {
            // 如果所有评分都失败，使用默认分数
            this.overallScore = 50.0;
        }

        // 确保分数不低于0分（允许超过100分）
        if (this.overallScore < 0) {
            this.overallScore = 0.0;
        }
    }

    /**
     * AI评分状态枚举
     */
    public enum AIScoringStatus {
        PENDING("待评分"),
        IN_PROGRESS("评分中"),
        COMPLETED("评分完成"),
        FAILED("评分失败");

        private final String description;

        AIScoringStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
