package com.photoexhibition.repository;

import com.photoexhibition.entity.Face;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FaceRepository extends JpaRepository<Face, Long> {

    List<Face> findByPhotoId(Long photoId);

    void deleteByPhotoId(Long photoId);

    @Query("SELECT f FROM Face f LEFT JOIN f.person p LEFT JOIN f.photo ph " +
           "WHERE (:keyword IS NULL OR :keyword = '' " +
           "OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(ph.filename) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Face> searchFaces(@Param("keyword") String keyword, Pageable pageable);

    Page<Face> findByPersonIsNull(Pageable pageable);

    List<Face> findByPersonIsNull();

    /**
     * 获取所有未分配人脸，预加载 Photo 关联
     * 用于相似度计算（避免懒加载问题）
     */
    @Query("SELECT DISTINCT f FROM Face f LEFT JOIN FETCH f.photo WHERE f.person IS NULL")
    List<Face> findByPersonIsNullWithPhoto();

    long countByPersonIsNull();

    Page<Face> findByPersonIsNotNull(Pageable pageable);

    Page<Face> findByPersonId(Long personId, Pageable pageable);

    List<Face> findByPersonId(Long personId);

    /**
     * 按人物ID获取人脸，按照片拍摄时间倒序
     * 去重：每张照片只保留一条记录（优先保留 id 最大的，即最新录入的）
     */
    @Query(value = "SELECT f.* FROM photo_face f " +
           "INNER JOIN photo p ON f.photo_id = p.id " +
           "WHERE f.person_id = :personId " +
           "AND f.id = (SELECT MAX(f2.id) FROM photo_face f2 WHERE f2.person_id = :personId AND f2.photo_id = p.id) " +
           "ORDER BY COALESCE(p.taken_at, p.created_at) DESC",
           countQuery = "SELECT COUNT(DISTINCT f.photo_id) FROM photo_face f WHERE f.person_id = :personId",
           nativeQuery = true)
    Page<Face> findByPersonIdOrderByPhotoTimeDesc(@Param("personId") Long personId, Pageable pageable);

    Page<Face> findByPersonIdAndIsConfirmed(Long personId, Boolean isConfirmed, Pageable pageable);

    List<Face> findByPersonIdAndIsConfirmed(Long personId, Boolean isConfirmed);

    /**
     * 批量获取所有已确认人物的人脸（用于相似度计算优化）
     * 返回: [personId, personName, embedding]
     * 注意：不使用 DISTINCT，以获取人物的所有人脸来计算平均 embedding
     */
    @Query("SELECT p.id, p.name, f.embedding FROM Face f JOIN f.person p WHERE f.isConfirmed = true AND f.embedding IS NOT NULL AND f.embedding <> ''")
    List<Object[]> findAllConfirmedPersonFacesWithEmbedding();

    @Query("SELECT f FROM Face f JOIN f.photo p WHERE f.person IS NULL AND p.albumId IN :albumIds")
    List<Face> findByPersonIsNullAndPhotoAlbumIdIn(@Param("albumIds") java.util.Set<Long> albumIds);

    /**
     * 按相册ID获取人脸（避免 getSimilarFacesForAlbum 中的全库扫描）
     * 注意：这里通过 JOIN photo 来限定 albumId。
     */
    @Query("SELECT f FROM Face f JOIN f.photo p WHERE p.albumId = :albumId")
    List<Face> findByPhotoAlbumId(@Param("albumId") Long albumId);

    /**
     * 仅获取具有 embedding 的人脸（避免在相似度计算时拉取/解析大量空 embedding 记录）
     */
    @Query("SELECT f FROM Face f WHERE f.embedding IS NOT NULL AND f.embedding <> ''")
    List<Face> findAllWithEmbedding();

    Face findTopByPersonIdOrderByConfidenceDescCreatedAtDesc(Long personId);

    Face findTopByPersonIdOrderByCreatedAtDesc(Long personId);

    /**
     * 批量获取每个人物的人脸数量（避免 N+1 查询）
     * 返回: [personId, faceCount]
     */
    @Query("SELECT f.person.id, COUNT(f) FROM Face f WHERE f.person IS NOT NULL GROUP BY f.person.id")
    List<Object[]> countFacesByPersonGrouped();

    /**
     * 查找重复的人脸记录（按照片分组，统计每张照片的人脸数量）
     */
    @Query("SELECT f.photo.id, COUNT(f) FROM Face f GROUP BY f.photo.id HAVING COUNT(f) > 1")
    List<Object[]> findDuplicateFacesByPhoto();

    /**
     * 按照片ID查找人脸记录，按创建时间倒序排列
     */
    List<Face> findByPhotoIdOrderByCreatedAtDesc(Long photoId);

    /**
     * 获取某人物在指定相册中的最佳照片（按点赞数 > 评分 > 创建时间）
     */
    @Query("SELECT f FROM Face f JOIN f.photo p WHERE f.person.id = :personId AND p.albumId = :albumId " +
           "ORDER BY COALESCE(p.likeCount, 0) DESC, COALESCE(p.qualityScore, 0) DESC, p.createdAt DESC")
    List<Face> findBestFaceByPersonAndAlbum(
            @Param("personId") Long personId,
            @Param("albumId") Long albumId);

    /**
     * 获取某人物所在的所有相册ID（按相册时间倒序，使用相册ID倒序近似代替）
     */
    @Query("SELECT DISTINCT p.albumId FROM Face f JOIN f.photo p WHERE f.person.id = :personId " +
           "ORDER BY p.albumId DESC")
    List<Long> findDistinctAlbumIdsByPersonId(@Param("personId") Long personId);

    /**
     * 统计某人物的人脸数量
     */
    long countByPersonId(Long personId);

    /**
     * 获取指定相册中所有已确认的人物及其人脸数量（按人脸数量倒序）
     * 返回: [personId, faceCount]
     */
    @Query("SELECT p.id, COUNT(f) as cnt FROM Face f JOIN f.person p " +
           "WHERE f.isConfirmed = true AND f.photo.albumId = :albumId " +
           "GROUP BY p.id ORDER BY cnt DESC")
    List<Object[]> findPersonIdsWithFaceCountByAlbumId(@Param("albumId") Long albumId);
}

