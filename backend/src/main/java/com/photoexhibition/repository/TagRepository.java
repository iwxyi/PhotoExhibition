package com.photoexhibition.repository;

import com.photoexhibition.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findByName(String name);

    @Modifying
    @Query(value = "DELETE FROM album_tag WHERE tag_id = :tagId", nativeQuery = true)
    void removeFromAlbums(Long tagId);

}

