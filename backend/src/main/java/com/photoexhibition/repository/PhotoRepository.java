package com.photoexhibition.repository;

import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.ProcessingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PhotoRepository extends JpaRepository<Photo, Long> {
    Optional<Photo> findByOriginalPath(String originalPath);

    Optional<Photo> findByContentHash(String contentHash);

    Optional<Photo> findByPathHash(String pathHash);

    Page<Photo> findByAlbumId(Long albumId, Pageable pageable);

    Long countByAlbumId(Long albumId);

    @Query("SELECT p FROM Photo p WHERE p.qualityScore >= :minScore ORDER BY RAND()")
    List<Photo> findRandomHighQualityPhotos(@Param("minScore") Double minScore, Pageable pageable);
    
    @Query("SELECT COUNT(p) FROM Photo p WHERE p.qualityScore >= :minScore")
    Long countByQualityScoreGreaterThanEqual(@Param("minScore") Double minScore);

    @Query(
        value = "SELECT * FROM photo p WHERE " +
                "(:cameraModel IS NULL OR p.camera_model = :cameraModel) AND " +
                "(:lensModel IS NULL OR p.lens_model = :lensModel) AND " +
                "(:minAperture IS NULL OR p.aperture_value >= :minAperture) AND " +
                "(:maxAperture IS NULL OR p.aperture_value <= :maxAperture) AND " +
                "(:minFocalLength IS NULL OR p.focal_length_mm >= :minFocalLength) AND " +
                "(:maxFocalLength IS NULL OR p.focal_length_mm <= :maxFocalLength) AND " +
                "(:minShutterSpeed IS NULL OR p.shutter_speed_seconds >= :minShutterSpeed) AND " +
                "(:maxShutterSpeed IS NULL OR p.shutter_speed_seconds <= :maxShutterSpeed) AND " +
                "(:minIso IS NULL OR p.iso >= :minIso) AND " +
                "(:maxIso IS NULL OR p.iso <= :maxIso) AND " +
                "(:dominantColor IS NULL OR p.dominant_color = :dominantColor) AND " +
                "(:minQualityScore IS NULL OR p.quality_score >= :minQualityScore) AND " +
                "(p.id NOT IN :excludePhotoIds)",
        countQuery = "SELECT count(*) FROM photo p WHERE " +
                "(:cameraModel IS NULL OR p.camera_model = :cameraModel) AND " +
                "(:lensModel IS NULL OR p.lens_model = :lensModel) AND " +
                "(:minAperture IS NULL OR p.aperture_value >= :minAperture) AND " +
                "(:maxAperture IS NULL OR p.aperture_value <= :maxAperture) AND " +
                "(:minFocalLength IS NULL OR p.focal_length_mm >= :minFocalLength) AND " +
                "(:maxFocalLength IS NULL OR p.focal_length_mm <= :maxFocalLength) AND " +
                "(:minShutterSpeed IS NULL OR p.shutter_speed_seconds >= :minShutterSpeed) AND " +
                "(:maxShutterSpeed IS NULL OR p.shutter_speed_seconds <= :maxShutterSpeed) AND " +
                "(:minIso IS NULL OR p.iso >= :minIso) AND " +
                "(:maxIso IS NULL OR p.iso <= :maxIso) AND " +
                "(:dominantColor IS NULL OR p.dominant_color = :dominantColor) AND " +
                "(:minQualityScore IS NULL OR p.quality_score >= :minQualityScore) AND " +
                "(p.id NOT IN :excludePhotoIds)",
        nativeQuery = true
    )
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
                                  @Param("dominantColor") String dominantColor,
                                  @Param("minQualityScore") Double minQualityScore,
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
     * 按人物ID筛选照片（用于人物图墙）
     */
    @Query(
        value = "SELECT DISTINCT p.* FROM photo p " +
                "INNER JOIN photo_face f ON p.id = f.photo_id " +
                "WHERE f.person_id = :personId",
        countQuery = "SELECT COUNT(DISTINCT p.id) FROM photo p " +
                "INNER JOIN photo_face f ON p.id = f.photo_id " +
                "WHERE f.person_id = :personId",
        nativeQuery = true
    )
    Page<Photo> findByPersonId(@Param("personId") Long personId, Pageable pageable);

    List<Photo> findByIsFeaturedTrueOrderByQualityScoreDesc();

    /**
     * 获取相册最早拍摄时间的照片
     */
    Optional<Photo> findTopByAlbumIdOrderByTakenAtAsc(Long albumId);

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
     * 获取焦距范围 [最小值, 最大值]
     */
    @Query(value = "SELECT MIN(p.focal_length_mm), MAX(p.focal_length_mm) FROM photo p WHERE p.focal_length_mm IS NOT NULL", nativeQuery = true)
    Double[] findFocalLengthRange();

    /**
     * 获取快门速度范围 [最小值, 最大值]
     */
    @Query(value = "SELECT MIN(p.shutter_speed_seconds), MAX(p.shutter_speed_seconds) FROM photo p WHERE p.shutter_speed_seconds IS NOT NULL", nativeQuery = true)
    Double[] findShutterSpeedRange();

    /**
     * 获取光圈范围 [最小值, 最大值]
     */
    @Query(value = "SELECT MIN(p.aperture_value), MAX(p.aperture_value) FROM photo p WHERE p.aperture_value IS NOT NULL", nativeQuery = true)
    Double[] findApertureRange();

    /**
     * 获取ISO范围 [最小值, 最大值]
     */
    @Query(value = "SELECT MIN(p.iso), MAX(p.iso) FROM photo p WHERE p.iso IS NOT NULL", nativeQuery = true)
    Integer[] findIsoRange();
}

