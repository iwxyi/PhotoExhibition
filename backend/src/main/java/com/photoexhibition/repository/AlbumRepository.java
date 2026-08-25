package com.photoexhibition.repository;

import com.photoexhibition.entity.Album;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlbumRepository extends JpaRepository<Album, Long> {
    Optional<Album> findByPath(String path);

    List<Album> findByUserIdIsNull();

    List<Album> findByUserIdIsNotNull();

    List<Album> findByUserId(Long userId);

    Page<Album> findByUserId(Long userId, Pageable pageable);

    long countByUserId(Long userId);

    List<Album> findByPhotoCountGreaterThan(Integer minPhotoCount);

    List<Album> findByUserIdAndPhotoCountGreaterThan(Long userId, Integer minPhotoCount);

    @Query("SELECT DISTINCT a FROM Album a JOIN a.tags t WHERE t.id IN :tagIds")
    Page<Album> findByTagIds(@Param("tagIds") List<Long> tagIds, Pageable pageable);

    List<Album> findAllByOrderByCreatedAtDesc();
    
    /**
     * 查询有照片的相册（photoCount > 0）
     */
    @EntityGraph(attributePaths = "tags")
    @Query("SELECT a FROM Album a WHERE a.photoCount > 0")
    Page<Album> findAlbumsWithPhotos(Pageable pageable);

    /**
     * 查询有照片的相册或开启了聚合功能的相册
     */
    @EntityGraph(attributePaths = "tags")
    @Query("SELECT a FROM Album a WHERE a.photoCount > 0 OR a.aggregateSubAlbums = true")
    Page<Album> findAlbumsWithPhotosOrAggregation(Pageable pageable);
    
    /**
     * 根据标签查询有照片的相册
     */
    @Query("SELECT DISTINCT a FROM Album a JOIN a.tags t WHERE t.id IN :tagIds AND a.photoCount > 0")
    Page<Album> findByTagIdsWithPhotos(@Param("tagIds") List<Long> tagIds, Pageable pageable);

    /**
     * 根据标签查询有照片的相册或开启了聚合功能的相册
     */
    @Query("SELECT DISTINCT a FROM Album a LEFT JOIN a.tags t WHERE (t.id IN :tagIds AND a.photoCount > 0) OR a.aggregateSubAlbums = true")
    Page<Album> findByTagIdsWithPhotosOrAggregation(@Param("tagIds") List<Long> tagIds, Pageable pageable);

    /**
     * 查询路径前缀匹配的相册
     */
    List<Album> findByPathStartingWith(String pathPrefix);

    /**
     * 查找以指定路径开头的所有相册（支持标准化路径，兼容Windows和Unix路径）
     */
    @Query("SELECT a FROM Album a WHERE (REPLACE(a.path, '\\\\', '/') LIKE CONCAT(REPLACE(:pathPrefix, '\\\\', '/'), '%') OR REPLACE(a.path, '\\\\', '/') LIKE CONCAT('/', CONCAT(REPLACE(:pathPrefix, '\\\\', '/'), '%')))")
    List<Album> findByPathStartingWithNormalized(@Param("pathPrefix") String pathPrefix);

    @Query("SELECT a FROM Album a WHERE a.userId = :userId AND (REPLACE(a.path, '\\\\', '/') LIKE CONCAT(REPLACE(:pathPrefix, '\\\\', '/'), '%') OR REPLACE(a.path, '\\\\', '/') LIKE CONCAT('/', CONCAT(REPLACE(:pathPrefix, '\\\\', '/'), '%')))")
    List<Album> findByUserIdAndPathStartingWithNormalized(@Param("userId") Long userId, @Param("pathPrefix") String pathPrefix);

    /**
     * 删除路径前缀匹配的相册
     */
    void deleteByPathStartingWith(String pathPrefix);

    /**
     * 路径前缀 + 有照片 的分页查询
     */
    @EntityGraph(attributePaths = "tags")
    Page<Album> findByPathStartingWithAndPhotoCountGreaterThan(String pathPrefix, Integer minPhotoCount, Pageable pageable);

    /**
     * 路径前缀 + 有照片 的分页查询（支持标准化路径，兼容Windows和Unix路径）
     */
    @Query(value = "SELECT a FROM Album a LEFT JOIN FETCH a.tags WHERE (REPLACE(a.path, '\\\\', '/') LIKE CONCAT(REPLACE(:pathPrefix, '\\\\', '/'), '%') OR REPLACE(a.path, '\\\\', '/') LIKE CONCAT('/', CONCAT(REPLACE(:pathPrefix, '\\\\', '/'), '%'))) AND a.photoCount > :minPhotoCount",
           countQuery = "SELECT COUNT(a) FROM Album a WHERE (REPLACE(a.path, '\\\\', '/') LIKE CONCAT(REPLACE(:pathPrefix, '\\\\', '/'), '%') OR REPLACE(a.path, '\\\\', '/') LIKE CONCAT('/', CONCAT(REPLACE(:pathPrefix, '\\\\', '/'), '%'))) AND a.photoCount > :minPhotoCount")
    Page<Album> findByPathStartingWithAndPhotoCountGreaterThanNormalized(String pathPrefix, Integer minPhotoCount, Pageable pageable);

    /**
     * 带标签的相册加载，避免懒加载问题
     */
    @Query("SELECT a FROM Album a LEFT JOIN FETCH a.tags WHERE a.id = :id")
    Optional<Album> findByIdWithTags(@Param("id") Long id);

    Optional<Album> findByPathHash(String pathHash);

    /**
     * 查找指定相册的所有直接子相册
     */
    @Query("SELECT a FROM Album a WHERE a.path LIKE CONCAT(:parentPath, '/%') AND a.path NOT LIKE CONCAT(:parentPath, '/%/%')")
    List<Album> findDirectSubAlbums(@Param("parentPath") String parentPath);

    /**
     * 查找指定相册的所有直接子相册（支持标准化路径，兼容Windows和Unix路径）
     * 通过路径层级来判断：子相册的路径层级 = 父相册路径层级 + 1
     */
    @Query(value = "SELECT a FROM Album a WHERE " +
           "(REPLACE(a.path, '\\\\', '/') LIKE :parentPathLike ESCAPE '\\\\' " +
           "OR REPLACE(a.path, '\\\\', '/') LIKE :parentPathLikeWithSlash ESCAPE '\\\\')",
           countQuery = "SELECT COUNT(a) FROM Album a WHERE " +
           "(REPLACE(a.path, '\\\\', '/') LIKE :parentPathLike ESCAPE '\\\\' " +
           "OR REPLACE(a.path, '\\\\', '/') LIKE :parentPathLikeWithSlash ESCAPE '\\\\')")
    List<Album> findDirectSubAlbumsNormalized(@Param("parentPath") String parentPath,
                                              @Param("parentPathLike") String parentPathLike,
                                              @Param("parentPathLikeWithSlash") String parentPathLikeWithSlash);

    /**
     * 简化查询：通过路径前缀查找子相册（忽略前导斜杠差异）
     */
    @Query("SELECT a FROM Album a WHERE REPLACE(a.path, '\\\\', '/') LIKE :prefix1 OR REPLACE(a.path, '\\\\', '/') LIKE :prefix2")
    List<Album> findByPathPrefixes(@Param("prefix1") String prefix1, @Param("prefix2") String prefix2);

    /**
     * 查找所有开启了聚合下级相册功能的相册
     */
    @EntityGraph(attributePaths = "tags")
    @Query("SELECT a FROM Album a WHERE a.aggregateSubAlbums = true")
    List<Album> findAlbumsWithAggregationEnabled();

    /**
     * 根据名称模糊搜索相册（用于短链接）
     */
    @Query("SELECT a FROM Album a WHERE a.name LIKE %:name% ORDER BY a.photoCount DESC")
    List<Album> searchByName(@Param("name") String name);

    /**
     * 根据路径模糊搜索相册（用于全局搜索）
     */
    @Query("SELECT a FROM Album a WHERE a.path LIKE %:path% ORDER BY a.photoCount DESC")
    List<Album> searchByPath(@Param("path") String path);

    /**
     * 查找以指定路径前缀开头且有照片的相册
     */
    @Query("SELECT a FROM Album a WHERE a.path LIKE :pathPrefix% AND a.photoCount > :minPhotoCount")
    List<Album> findByPathPrefixWithPhotos(@Param("pathPrefix") String pathPrefix, @Param("minPhotoCount") int minPhotoCount);

    @Query(value = "SELECT * FROM album a " +
            "WHERE a.user_id = :userId " +
            "AND (REPLACE(a.path, '\\\\', '/') LIKE CONCAT('%/', :userId, '/', :category, '/%') " +
            "OR REPLACE(a.path, '\\\\', '/') LIKE CONCAT('%/', :userId, '/', :category))",
           nativeQuery = true)
    List<Album> findByUserIdAndTopLevelCategory(@Param("userId") Long userId, @Param("category") String category);

    @Query(value = "SELECT * FROM album a " +
            "WHERE (" +
            "  a.user_id IS NOT NULL AND (" +
            "    REPLACE(a.path, '\\\\', '/') LIKE CONCAT('%/', a.user_id, '/', :category, '/%') " +
            "    OR REPLACE(a.path, '\\\\', '/') LIKE CONCAT('%/', a.user_id, '/', :category)" +
            "  )" +
            ") OR (" +
            "  a.user_id IS NULL AND (" +
            "    REPLACE(a.path, '\\\\', '/') LIKE CONCAT('%/', :category, '/%') " +
            "    OR REPLACE(a.path, '\\\\', '/') LIKE CONCAT('%/', :category)" +
            "  )" +
            ")",
           nativeQuery = true)
    List<Album> findByTopLevelCategory(@Param("category") String category);
}
