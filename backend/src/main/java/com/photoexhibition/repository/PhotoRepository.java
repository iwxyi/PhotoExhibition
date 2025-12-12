package com.photoexhibition.repository;

import com.photoexhibition.entity.Photo;
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
                "(:minAperture IS NULL OR CAST(REPLACE(p.aperture, 'f/', '') AS DECIMAL(10,3)) >= :minAperture) AND " +
                "(:maxAperture IS NULL OR CAST(REPLACE(p.aperture, 'f/', '') AS DECIMAL(10,3)) <= :maxAperture) AND " +
                "(:minIso IS NULL OR p.iso >= :minIso) AND " +
                "(:maxIso IS NULL OR p.iso <= :maxIso)",
        countQuery = "SELECT count(*) FROM photo p WHERE " +
                "(:cameraModel IS NULL OR p.camera_model = :cameraModel) AND " +
                "(:lensModel IS NULL OR p.lens_model = :lensModel) AND " +
                "(:minAperture IS NULL OR CAST(REPLACE(p.aperture, 'f/', '') AS DECIMAL(10,3)) >= :minAperture) AND " +
                "(:maxAperture IS NULL OR CAST(REPLACE(p.aperture, 'f/', '') AS DECIMAL(10,3)) <= :maxAperture) AND " +
                "(:minIso IS NULL OR p.iso >= :minIso) AND " +
                "(:maxIso IS NULL OR p.iso <= :maxIso)",
        nativeQuery = true
    )
    Page<Photo> findByExifFilters(@Param("cameraModel") String cameraModel,
                                  @Param("lensModel") String lensModel,
                                  @Param("minAperture") Double minAperture,
                                  @Param("maxAperture") Double maxAperture,
                                  @Param("minIso") Integer minIso,
                                  @Param("maxIso") Integer maxIso,
                                  Pageable pageable);

    @Query(value = "SELECT DISTINCT p.* FROM photo p " +
           "INNER JOIN photo_tag pt ON p.id = pt.photo_id " +
           "WHERE pt.tag_id IN :tagIds", nativeQuery = true)
    Page<Photo> findByTagIds(@Param("tagIds") List<Long> tagIds, Pageable pageable);

    List<Photo> findByIsFeaturedTrueOrderByQualityScoreDesc();

    /**
     * 获取相册最早拍摄时间的照片
     */
    Optional<Photo> findTopByAlbumIdOrderByTakenAtAsc(Long albumId);

    /**
     * 路径前缀查询
     */
    List<Photo> findByOriginalPathStartingWith(String pathPrefix);

    /**
     * 按相册ID批量删除
     */
    void deleteByAlbumIdIn(List<Long> albumIds);
}

