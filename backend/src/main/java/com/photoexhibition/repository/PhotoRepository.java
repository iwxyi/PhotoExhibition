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

    Page<Photo> findByAlbumId(Long albumId, Pageable pageable);

    Long countByAlbumId(Long albumId);

    @Query("SELECT p FROM Photo p WHERE p.qualityScore >= :minScore ORDER BY RAND()")
    List<Photo> findRandomHighQualityPhotos(@Param("minScore") Double minScore, Pageable pageable);

    @Query("SELECT p FROM Photo p WHERE " +
           "(:cameraModel IS NULL OR p.cameraModel = :cameraModel) AND " +
           "(:lensModel IS NULL OR p.lensModel = :lensModel) AND " +
           "(:minAperture IS NULL OR CAST(REPLACE(p.aperture, 'f/', '') AS double) >= :minAperture) AND " +
           "(:maxAperture IS NULL OR CAST(REPLACE(p.aperture, 'f/', '') AS double) <= :maxAperture) AND " +
           "(:minIso IS NULL OR p.iso >= :minIso) AND " +
           "(:maxIso IS NULL OR p.iso <= :maxIso)")
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
}

