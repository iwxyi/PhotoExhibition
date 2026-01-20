package com.photoexhibition.repository;

import com.photoexhibition.entity.PhotoAIScoring;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AI评分Repository
 */
@Repository
public interface PhotoAIScoringRepository extends JpaRepository<PhotoAIScoring, Long> {

    /**
     * 根据照片ID查找AI评分
     */
    Optional<PhotoAIScoring> findByPhotoId(Long photoId);

    /**
     * 根据照片ID列表批量查找AI评分
     */
    @Query("SELECT s FROM PhotoAIScoring s WHERE s.photoId IN :photoIds")
    List<PhotoAIScoring> findByPhotoIds(@Param("photoIds") List<Long> photoIds);

    /**
     * 查找需要重新评分的照片（评分失败或版本过旧）
     */
    @Query("SELECT s FROM PhotoAIScoring s WHERE s.scoringStatus = 'FAILED' OR s.scoreVersion < :currentVersion")
    List<PhotoAIScoring> findPhotosNeedingRescoring(@Param("currentVersion") String currentVersion);

    /**
     * 统计已完成的AI评分数量
     */
    @Query("SELECT COUNT(s) FROM PhotoAIScoring s WHERE s.scoringStatus = 'COMPLETED'")
    Long countCompletedScorings();

    /**
     * 查找综合评分最高的照片
     */
    @Query("SELECT s FROM PhotoAIScoring s WHERE s.scoringStatus = 'COMPLETED' ORDER BY s.overallScore DESC")
    List<PhotoAIScoring> findTopScoredPhotos(org.springframework.data.domain.Pageable pageable);

    /**
     * 根据综合评分范围查找照片
     */
    @Query("SELECT s FROM PhotoAIScoring s WHERE s.overallScore BETWEEN :minScore AND :maxScore AND s.scoringStatus = 'COMPLETED'")
    List<PhotoAIScoring> findByScoreRange(@Param("minScore") Double minScore, @Param("maxScore") Double maxScore);

    /**
     * 删除照片对应的AI评分记录
     */
    void deleteByPhotoId(Long photoId);
}
