package com.photoexhibition.repository;

import com.photoexhibition.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.List;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByName(String name);

    Optional<Tag> findByNameAndUserId(String name, Long userId);

    Optional<Tag> findByIdAndUserId(Long id, Long userId);

    List<Tag> findByUserIdIsNull();

    List<Tag> findByUserIdOrderByNameAsc(Long userId);

    Page<Tag> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    @Modifying
    @Query(value = "DELETE FROM album_tag WHERE tag_id = :tagId", nativeQuery = true)
    void removeFromAlbums(Long tagId);

    @Modifying
    @Query(value = "DELETE FROM photo_tag WHERE tag_id = :tagId", nativeQuery = true)
    void removeFromPhotos(Long tagId);

    @Query(
        value = "SELECT t.id AS id, t.name AS name, t.color AS color, COUNT(pt.photo_id) AS photoCount " +
                "FROM tag t " +
                "LEFT JOIN photo_tag pt ON t.id = pt.tag_id " +
                "GROUP BY t.id, t.name, t.color " +
                "ORDER BY t.id",
        nativeQuery = true)
    List<TagCountProjection> findAllWithCount();

    @Query(
        value = "SELECT t.id AS id, t.name AS name, t.color AS color, COUNT(pt.photo_id) AS photoCount " +
                "FROM tag t " +
                "LEFT JOIN photo_tag pt ON t.id = pt.tag_id " +
                "LEFT JOIN photo p ON p.id = pt.photo_id " +
                "WHERE t.user_id = :userId AND (p.id IS NULL OR p.user_id = :userId) " +
                "GROUP BY t.id, t.name, t.color " +
                "ORDER BY t.id",
        nativeQuery = true)
    List<TagCountProjection> findAllWithCountByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);

    @Query(
        value = "SELECT t.id AS id, COUNT(pt.photo_id) AS photoCount " +
                "FROM tag t " +
                "LEFT JOIN photo_tag pt ON t.id = pt.tag_id " +
                "WHERE t.id IN :ids " +
                "GROUP BY t.id",
        nativeQuery = true)
    List<TagIdCountProjection> findCountsByIds(List<Long> ids);

    @Query(
        value = "SELECT t.id AS id, COUNT(pt.photo_id) AS photoCount " +
                "FROM tag t " +
                "LEFT JOIN photo_tag pt ON t.id = pt.tag_id " +
                "LEFT JOIN photo p ON p.id = pt.photo_id " +
                "WHERE t.id IN :ids AND t.user_id = :userId AND (p.id IS NULL OR p.user_id = :userId) " +
                "GROUP BY t.id",
        nativeQuery = true)
    List<TagIdCountProjection> findCountsByIdsAndUserId(@org.springframework.data.repository.query.Param("ids") List<Long> ids,
                                                        @org.springframework.data.repository.query.Param("userId") Long userId);

    interface TagCountProjection {
        Long getId();
        String getName();
        String getColor();
        Long getPhotoCount();
    }

    interface TagIdCountProjection {
        Long getId();
        Long getPhotoCount();
    }

    /**
     * 清空所有图片-标签关联（用于重新生成智能标签）
     */
    @Modifying
    @Query(value = "DELETE FROM photo_tag", nativeQuery = true)
    void clearAllPhotoTagAssociations();

    @Query("SELECT t FROM Tag t WHERE t.name LIKE %:keyword%")
    List<Tag> searchByNameContaining(@org.springframework.data.repository.query.Param("keyword") String keyword);
}
