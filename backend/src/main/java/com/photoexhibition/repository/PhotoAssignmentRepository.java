package com.photoexhibition.repository;

import com.photoexhibition.entity.PhotoAssignment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PhotoAssignmentRepository extends JpaRepository<PhotoAssignment, Long> {

    Optional<PhotoAssignment> findByPhotoId(Long photoId);

    @Query("SELECT pa FROM PhotoAssignment pa JOIN Photo p ON p.id = pa.photoId " +
        "WHERE pa.photoId = :photoId AND (:userId IS NULL OR p.userId = :userId)")
    Optional<PhotoAssignment> findByPhotoIdAndScopedUserId(@Param("photoId") Long photoId, @Param("userId") Long userId);

    Page<PhotoAssignment> findByPersonId(Long personId, Pageable pageable);

    List<PhotoAssignment> findByPersonId(Long personId);

    List<PhotoAssignment> findTop20ByOrderByCreatedAtDesc();

    @Query("SELECT pa FROM PhotoAssignment pa JOIN Photo p ON p.id = pa.photoId " +
        "WHERE (:userId IS NULL OR p.userId = :userId) ORDER BY pa.createdAt DESC")
    List<PhotoAssignment> findRecentByScopedUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT COUNT(pa) FROM PhotoAssignment pa JOIN Photo p ON p.id = pa.photoId " +
        "WHERE (:userId IS NULL OR p.userId = :userId)")
    long countByScopedUserId(@Param("userId") Long userId);

    void deleteByPhotoId(Long photoId);

    /**
     * 删除某个人物的所有“图片指派”记录（用于解散/删除人物前清理外键引用）
     */
    void deleteByPersonId(Long personId);

    /**
     * 统计指定人物在一批相册中“已认领”的图片数量（含：人脸绑定到人物、以及图片指派到人物）
     * 返回行格式：[albumId, cnt]
     */
    @Query(
        value = "SELECT p.album_id AS albumId, COUNT(DISTINCT p.id) AS cnt " +
                "FROM photo p " +
                "LEFT JOIN photo_face pf ON pf.photo_id = p.id AND pf.person_id = :personId " +
                "LEFT JOIN photo_assignment pa ON pa.photo_id = p.id AND pa.person_id = :personId " +
                "WHERE p.album_id IN (:albumIds) AND (pf.id IS NOT NULL OR pa.id IS NOT NULL) " +
                "GROUP BY p.album_id",
        nativeQuery = true
    )
    List<Object[]> countClaimedPhotosByAlbumIds(@Param("personId") Long personId, @Param("albumIds") List<Long> albumIds);
}
