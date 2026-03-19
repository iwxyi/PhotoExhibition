package com.photoexhibition.repository;

import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.ProcessingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface PhotoRepository extends JpaRepository<Photo, Long> {
    /**
     * 一次性加载 Photo 及其关联集合，避免在非事务/异步线程里触发懒加载异常。
     *
     * 注意：faces/tags 同时 join fetch 会造成行数膨胀，因此使用 DISTINCT 去重。
     */
    @Query("SELECT DISTINCT p FROM Photo p " +
           "LEFT JOIN FETCH p.faces " +
           "LEFT JOIN FETCH p.tags " +
           "WHERE p.id = :id")
    Optional<Photo> findByIdWithFacesAndTags(@Param("id") Long id);

    Optional<Photo> findByOriginalPath(String originalPath);

    @Query("SELECT p FROM Photo p WHERE p.originalPath = :originalPath ORDER BY p.id")
    List<Photo> findAllByOriginalPath(@Param("originalPath") String originalPath);

    Optional<Photo> findByContentHash(String contentHash);

    Optional<Photo> findByPathHash(String pathHash);

    Page<Photo> findByAlbumId(Long albumId, Pageable pageable);

    /**
     * 获取相册中未隐藏的照片（排除隐藏的照片）
     */
    @Query("SELECT p FROM Photo p WHERE p.albumId = :albumId AND (p.isHidden IS NULL OR p.isHidden = false)")
    Page<Photo> findByAlbumIdAndIsHiddenFalse(@Param("albumId") Long albumId, Pageable pageable);

    /**
     * 统计相册中未隐藏的照片数量
     */
    @Query("SELECT COUNT(p) FROM Photo p WHERE p.albumId = :albumId AND (p.isHidden IS NULL OR p.isHidden = false)")
    Long countByAlbumIdAndIsHiddenFalse(@Param("albumId") Long albumId);

    Long countByAlbumId(Long albumId);

    /**
     * 批量清除所有照片的背景移除路径记录
     */
    @Modifying
    @Transactional
    @Query("UPDATE Photo p SET p.backgroundRemovedPath = NULL WHERE p.backgroundRemovedPath IS NOT NULL")
    int clearAllBackgroundRemovedPath();

    @Query("SELECT p FROM Photo p WHERE p.qualityScore >= :minScore ORDER BY RAND()")
    List<Photo> findRandomHighQualityPhotos(@Param("minScore") Double minScore, Pageable pageable);

    /**
     * 随机获取高质量且未隐藏的照片
     */
    @Query("SELECT p FROM Photo p WHERE p.qualityScore >= :minScore AND (p.isHidden IS NULL OR p.isHidden = false) ORDER BY RAND()")
    List<Photo> findRandomHighQualityPhotosNotHidden(@Param("minScore") Double minScore, Pageable pageable);

    @Query(
        value = "SELECT * FROM photo WHERE (is_hidden = 0 OR is_hidden IS NULL) ORDER BY RAND()",
        countQuery = "SELECT count(*) FROM photo WHERE is_hidden = 0 OR is_hidden IS NULL",
        nativeQuery = true
    )
    Page<Photo> findAllRandomNotHidden(Pageable pageable);

    @Query(
        value = "SELECT * FROM photo ORDER BY RAND()",
        countQuery = "SELECT count(*) FROM photo",
        nativeQuery = true
    )
    Page<Photo> findAllRandom(Pageable pageable);
    
    @Query("SELECT COUNT(p) FROM Photo p WHERE p.qualityScore >= :minScore")
    Long countByQualityScoreGreaterThanEqual(@Param("minScore") Double minScore);

    @Query("SELECT p FROM Photo p WHERE " +
           "(:cameraModel IS NULL OR p.cameraModel = :cameraModel) AND " +
           "(:lensModel IS NULL OR p.lensModel = :lensModel) AND " +
           "(:minAperture IS NULL OR p.apertureValue >= :minAperture) AND " +
           "(:maxAperture IS NULL OR p.apertureValue <= :maxAperture) AND " +
           "(:minFocalLength IS NULL OR p.focalLengthMm >= :minFocalLength) AND " +
           "(:maxFocalLength IS NULL OR p.focalLengthMm <= :maxFocalLength) AND " +
           "(:minShutterSpeed IS NULL OR p.shutterSpeedSeconds >= :minShutterSpeed) AND " +
           "(:maxShutterSpeed IS NULL OR p.shutterSpeedSeconds <= :maxShutterSpeed) AND " +
           "(:minIso IS NULL OR p.iso >= :minIso) AND " +
           "(:maxIso IS NULL OR p.iso <= :maxIso) AND " +
           "(:colorCategory IS NULL OR p.colorCategory = :colorCategory) AND " +
           "(:minQualityScore IS NULL OR p.qualityScore >= :minQualityScore) AND " +
           "(:startDate IS NULL OR p.takenAt >= :startDate) AND " +
           "(:endDate IS NULL OR p.takenAt <= :endDate) AND " +
           "(p.id NOT IN :excludePhotoIds)")
    Page<Photo> findByExifFilters(@Param("cameraModel") String cameraModel,
                                  @Param("lensModel") String lensModel,
                                  @Param("minAperture") Double minAperture,
                                  @Param("maxAperture") Double maxAperture,
                                  @Param("minFocalLength") Double minFocalLength,
                                  @Param("maxFocalLength") Double maxFocalLength,
                                  @Param("minShutterSpeed") Double minShutterSpeed,
                                  @Param("maxShutterSpeed") Double maxShutterSpeed,
                                  @Param("minIso") Integer minIso,
                                  @Param("maxIso") Integer maxIso,
                                  @Param("colorCategory") String colorCategory,
                                  @Param("minQualityScore") Double minQualityScore,
                                  @Param("startDate") java.time.LocalDateTime startDate,
                                  @Param("endDate") java.time.LocalDateTime endDate,
                                  @Param("excludePhotoIds") List<Long> excludePhotoIds,
                                  Pageable pageable);

    @Query(
        value = "SELECT DISTINCT p.* FROM photo p " +
                "INNER JOIN photo_tag pt ON p.id = pt.photo_id " +
                "WHERE pt.tag_id IN (:tagIds)",
        countQuery = "SELECT COUNT(DISTINCT p.id) FROM photo p " +
           "INNER JOIN photo_tag pt ON p.id = pt.photo_id " +
                     "WHERE pt.tag_id IN (:tagIds)",
        nativeQuery = true
    )
    Page<Photo> findByTagIds(@Param("tagIds") List<Long> tagIds, Pageable pageable);

    /**
     * 按人物ID筛选照片（用于人物图墙，排除隐藏的照片）
     */
    @Query(
        value = "SELECT DISTINCT p.* FROM photo p " +
                "INNER JOIN photo_face f ON p.id = f.photo_id " +
                "WHERE f.person_id = :personId AND (p.is_hidden = 0 OR p.is_hidden IS NULL)",
        countQuery = "SELECT COUNT(DISTINCT p.id) FROM photo p " +
                "INNER JOIN photo_face f ON p.id = f.photo_id " +
                "WHERE f.person_id = :personId AND (p.is_hidden = 0 OR p.is_hidden IS NULL)",
        nativeQuery = true
    )
    Page<Photo> findByPersonId(@Param("personId") Long personId, Pageable pageable);

    /**
     * 按颜色类别筛选照片
     */
    @Query(
        value = "SELECT * FROM photo p WHERE p.color_category = :colorCategory AND (:minQualityScore IS NULL OR p.quality_score >= :minQualityScore) AND (p.id NOT IN :excludePhotoIds)",
        countQuery = "SELECT count(*) FROM photo p WHERE p.color_category = :colorCategory AND (:minQualityScore IS NULL OR p.quality_score >= :minQualityScore) AND (p.id NOT IN :excludePhotoIds)",
        nativeQuery = true
    )
    Page<Photo> findByColorCategory(@Param("colorCategory") String colorCategory,
                                   @Param("minQualityScore") Double minQualityScore,
                                   @Param("excludePhotoIds") List<Long> excludePhotoIds,
                                   Pageable pageable);

    @Query("SELECT p FROM Photo p WHERE p.albumId IN :albumIds AND (p.isHidden IS NULL OR p.isHidden = false)")
    Page<Photo> findByAlbumIds(@Param("albumIds") List<Long> albumIds, Pageable pageable);

    // ---- 统计/聚合：用于过滤项（避免 photoRepository.findAll().stream() 全表加载） ----
    @Query("SELECT MIN(p.focalLengthMm), MAX(p.focalLengthMm) FROM Photo p WHERE p.focalLengthMm IS NOT NULL")
    Object[] findFocalLengthRange();

    @Query("SELECT MIN(p.shutterSpeedSeconds), MAX(p.shutterSpeedSeconds) FROM Photo p WHERE p.shutterSpeedSeconds IS NOT NULL")
    Object[] findShutterSpeedRange();

    @Query("SELECT MIN(p.apertureValue), MAX(p.apertureValue) FROM Photo p WHERE p.apertureValue IS NOT NULL")
    Object[] findApertureRange();

    @Query("SELECT MIN(p.iso), MAX(p.iso) FROM Photo p WHERE p.iso IS NOT NULL")
    Object[] findIsoRange();

    List<Photo> findByIsFeaturedTrueOrderByQualityScoreDesc();

    /**
     * 获取相册最早拍摄时间的照片
     */
    Optional<Photo> findTopByAlbumIdOrderByTakenAtAsc(Long albumId);

    /**
     * 获取相册中最晚的拍摄时间（EXIF时间）
     */
    Optional<Photo> findTopByAlbumIdOrderByTakenAtDesc(Long albumId);

    /**
     * 获取相册中最晚的文件修改时间
     */
    Optional<Photo> findTopByAlbumIdOrderByCreatedAtDesc(Long albumId);

    /**
     * 路径前缀查询
     */
    @Query("SELECT p FROM Photo p WHERE p.originalPath LIKE CONCAT(:pathPrefix, '%')")
    List<Photo> findByOriginalPathStartingWith(@Param("pathPrefix") String pathPrefix);

    /**
     * 路径前缀计数
     */
    @Query("SELECT COUNT(p) FROM Photo p WHERE p.originalPath LIKE CONCAT(:pathPrefix, '%')")
    Long countByOriginalPathStartingWith(@Param("pathPrefix") String pathPrefix);

    /**
     * 按相册ID批量删除
     */
    void deleteByAlbumIdIn(List<Long> albumIds);

    /**
     * 清空所有缩略图路径（用于重新生成缩略图）
     */
    @Query("UPDATE Photo p SET p.thumbnailPath = null, p.smallThumbPath = null, p.mediumThumbPath = null, p.largeThumbPath = null")
    @org.springframework.data.jpa.repository.Modifying
    void clearAllThumbnailPaths();

    /**
     * 清空所有人脸关联
     */
    @Query("DELETE FROM Face f")
    @org.springframework.data.jpa.repository.Modifying
    void clearAllFaceAssociations();

    /**
     * 统计指定相册在指定时间之后更新的照片数量
     */
    @Query("SELECT COUNT(p) FROM Photo p WHERE p.albumId = :albumId AND p.updatedAt > :since")
    Long countPhotosUpdatedAfter(@Param("albumId") Long albumId, @Param("since") java.time.LocalDateTime since);

    /**
     * 查找处理失败的照片
     */
    @Query("SELECT p FROM Photo p WHERE p.processingStatus = 'FAILED'")
    List<Photo> findFailedPhotos();

    /**
     * 查找未完成处理的照片（不包括失败的）
     */
    @Query("SELECT p FROM Photo p WHERE p.processingStatus != 'COMPLETED' AND p.processingStatus != 'FAILED'")
    List<Photo> findIncompletePhotos();

    /**
     * 查找需要重新处理的照片（包括失败的和未完成的）
     */
    @Query("SELECT p FROM Photo p WHERE p.processingStatus != 'COMPLETED'")
    List<Photo> findPhotosNeedingReprocessing();

    /**
     * 统计处理失败的照片数量
     */
    @Query("SELECT COUNT(p) FROM Photo p WHERE p.processingStatus = 'FAILED'")
    Long countFailedPhotos();

    /**
     * 统计未完成处理的照片数量
     */
    @Query("SELECT COUNT(p) FROM Photo p WHERE p.processingStatus != 'COMPLETED' AND p.processingStatus != 'FAILED'")
    Long countIncompletePhotos();

    /**
     * 统计指定处理状态的照片数量
     */
    @Query("SELECT COUNT(p) FROM Photo p WHERE p.processingStatus = :status")
    Long countPhotosByProcessingStatus(@Param("status") ProcessingStatus status);

    /**
     * 获取所有不重复的相机型号
     */
    @Query(value = "SELECT DISTINCT p.camera_model FROM photo p WHERE p.camera_model IS NOT NULL AND p.camera_model != '' ORDER BY p.camera_model", nativeQuery = true)
    List<String> findDistinctCameraModels();

    /**
     * 获取所有不重复的镜头型号
     */
    @Query(value = "SELECT DISTINCT p.lens_model FROM photo p WHERE p.lens_model IS NOT NULL AND p.lens_model != '' ORDER BY p.lens_model", nativeQuery = true)
    List<String> findDistinctLensModels();

    /**
     * 获取相机型号及对应的照片数量
     */
    @Query(value = "SELECT p.camera_model, COUNT(*) as count FROM photo p WHERE p.camera_model IS NOT NULL AND p.camera_model != '' GROUP BY p.camera_model ORDER BY count DESC, p.camera_model", nativeQuery = true)
    List<Object[]> findCameraModelsWithCount();

    /**
     * 获取镜头型号及对应的照片数量
     */
    @Query(value = "SELECT p.lens_model, COUNT(*) as count FROM photo p WHERE p.lens_model IS NOT NULL AND p.lens_model != '' GROUP BY p.lens_model ORDER BY count DESC, p.lens_model", nativeQuery = true)
    List<Object[]> findLensModelsWithCount();

    /**
     * 获取颜色分类及对应的照片数量
     */
    @Query(value = "SELECT p.color_category, COUNT(*) as count FROM photo p WHERE p.color_category IS NOT NULL AND p.color_category != '' GROUP BY p.color_category ORDER BY count DESC, p.color_category", nativeQuery = true)
    List<Object[]> findColorCategoriesWithCount();

    /**
     * 获取焦距范围 [最小值, 最大值]
     */
    @Query(value = "SELECT COUNT(*) FROM photo", nativeQuery = true)
    Long countAllPhotos();

    @Query(value = "SELECT COUNT(*) FROM photo WHERE focal_length_mm IS NOT NULL", nativeQuery = true)
    Long countPhotosWithFocalLength();

    @Query(value = "SELECT COUNT(*) FROM photo WHERE aperture_value IS NOT NULL", nativeQuery = true)
    Long countPhotosWithAperture();

    @Query(value = "SELECT COUNT(*) FROM photo WHERE shutter_speed_seconds IS NOT NULL", nativeQuery = true)
    Long countPhotosWithShutterSpeed();

    @Query(value = "SELECT COUNT(*) FROM photo WHERE iso IS NOT NULL", nativeQuery = true)
    Long countPhotosWithIso();

    /**
     * 查找包含人脸的照片
     */
    @Query(
        value = "SELECT DISTINCT p.* FROM photo p " +
                "INNER JOIN photo_face pf ON p.id = pf.photo_id",
        countQuery = "SELECT COUNT(DISTINCT p.id) FROM photo p " +
                "INNER JOIN photo_face pf ON p.id = pf.photo_id",
        nativeQuery = true
    )
    Page<Photo> findPhotosWithFaces(Pageable pageable);

    /**
     * 根据文件名模糊搜索照片
     */
    @Query("SELECT p FROM Photo p WHERE p.filename LIKE %:filename% AND (p.isHidden IS NULL OR p.isHidden = false)")
    List<Photo> searchByFilename(@Param("filename") String filename);

    /**
     * 按ID列表查询未隐藏的照片
     */
    @Query("SELECT p FROM Photo p WHERE p.id IN :ids AND (p.isHidden IS NULL OR p.isHidden = false)")
    Page<Photo> findByIdIn(@Param("ids") java.util.Collection<Long> ids, Pageable pageable);

    /**
     * 按ID列表查询未隐藏的照片（不分页）
     */
    @Query("SELECT p FROM Photo p WHERE p.id IN :ids AND (p.isHidden IS NULL OR p.isHidden = false)")
    List<Photo> findAllByIdIn(@Param("ids") java.util.Collection<Long> ids);

    // ===== 包含隐藏照片的查询变体（AI搜索用） =====

    /**
     * 根据文件名模糊搜索照片（包含隐藏）
     */
    @Query("SELECT p FROM Photo p WHERE p.filename LIKE %:filename%")
    List<Photo> searchByFilenameIncludeHidden(@Param("filename") String filename);

    /**
     * 按ID列表查询照片（包含隐藏，不分页）
     */
    @Query("SELECT p FROM Photo p WHERE p.id IN :ids")
    List<Photo> findAllByIdInIncludeHidden(@Param("ids") java.util.Collection<Long> ids);

    /**
     * 按相册ID列表查询照片（包含隐藏）
     */
    @Query("SELECT p FROM Photo p WHERE p.albumId IN :albumIds")
    Page<Photo> findByAlbumIdsIncludeHidden(@Param("albumIds") List<Long> albumIds, Pageable pageable);
}

