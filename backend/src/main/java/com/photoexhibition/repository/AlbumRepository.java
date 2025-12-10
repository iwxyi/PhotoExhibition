package com.photoexhibition.repository;

import com.photoexhibition.entity.Album;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlbumRepository extends JpaRepository<Album, Long> {
    Optional<Album> findByPath(String path);

    @Query("SELECT DISTINCT a FROM Album a JOIN a.tags t WHERE t.id IN :tagIds")
    Page<Album> findByTagIds(@Param("tagIds") List<Long> tagIds, Pageable pageable);

    List<Album> findAllByOrderByCreatedAtDesc();
    
    /**
     * 查询有照片的相册（photoCount > 0）
     */
    @Query("SELECT a FROM Album a WHERE a.photoCount > 0")
    Page<Album> findAlbumsWithPhotos(Pageable pageable);
    
    /**
     * 根据标签查询有照片的相册
     */
    @Query("SELECT DISTINCT a FROM Album a JOIN a.tags t WHERE t.id IN :tagIds AND a.photoCount > 0")
    Page<Album> findByTagIdsWithPhotos(@Param("tagIds") List<Long> tagIds, Pageable pageable);

    /**
     * 查询路径前缀匹配的相册
     */
    List<Album> findByPathStartingWith(String pathPrefix);

    /**
     * 删除路径前缀匹配的相册
     */
    void deleteByPathStartingWith(String pathPrefix);

    /**
     * 路径前缀 + 有照片 的分页查询
     */
    Page<Album> findByPathStartingWithAndPhotoCountGreaterThan(String pathPrefix, Integer minPhotoCount, Pageable pageable);
}

