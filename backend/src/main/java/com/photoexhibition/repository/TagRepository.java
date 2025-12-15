package com.photoexhibition.repository;

import com.photoexhibition.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByName(String name);

    @Modifying
    @Query(value = "DELETE FROM album_tag WHERE tag_id = :tagId", nativeQuery = true)
    void removeFromAlbums(Long tagId);

    @Query(
        value = "SELECT t.id AS id, t.name AS name, t.color AS color, COUNT(pt.photo_id) AS photoCount " +
                "FROM tag t " +
                "LEFT JOIN photo_tag pt ON t.id = pt.tag_id " +
                "GROUP BY t.id, t.name, t.color " +
                "ORDER BY t.id",
        nativeQuery = true)
    List<TagCountProjection> findAllWithCount();

    @Query(
        value = "SELECT t.id AS id, COUNT(pt.photo_id) AS photoCount " +
                "FROM tag t " +
                "LEFT JOIN photo_tag pt ON t.id = pt.tag_id " +
                "WHERE t.id IN :ids " +
                "GROUP BY t.id",
        nativeQuery = true)
    List<TagIdCountProjection> findCountsByIds(List<Long> ids);

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
}

