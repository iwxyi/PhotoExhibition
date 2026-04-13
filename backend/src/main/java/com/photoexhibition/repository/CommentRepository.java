package com.photoexhibition.repository;

import com.photoexhibition.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    void deleteByAlbumIdIn(List<Long> albumIds);

    /**
     * 根据相册ID查询顶级评论（parentId为null，未删除）
     */
    Page<Comment> findByAlbumIdAndParentIdIsNullAndDeletedFalseOrderByCreatedAtDesc(Long albumId, Pageable pageable);

    Page<Comment> findByAlbumIdAndUserIdAndParentIdIsNullAndDeletedFalseOrderByCreatedAtDesc(Long albumId, Long userId, Pageable pageable);

    /**
     * 根据父评论ID查询回复（未删除）
     */
    List<Comment> findByParentIdAndDeletedFalseOrderByCreatedAtAsc(Long parentId);

    List<Comment> findByParentIdAndUserIdAndDeletedFalseOrderByCreatedAtAsc(Long parentId, Long userId);

    /**
     * 根据相册ID查询所有评论（未删除）
     */
    List<Comment> findByAlbumIdAndDeletedFalseOrderByCreatedAtDesc(Long albumId);

    List<Comment> findByAlbumIdAndUserIdAndDeletedFalseOrderByCreatedAtDesc(Long albumId, Long userId);

    /**
     * 根据邮箱查询用户在指定时间范围内的评论数量
     */
    @Query("SELECT COUNT(c) FROM Comment c WHERE c.email = :email AND c.createdAt >= :startTime")
    long countByEmailAndCreatedAtAfter(@Param("email") String email, @Param("startTime") LocalDateTime startTime);

    /**
     * 根据标识符（邮箱或IP）查询用户在指定时间范围内的评论数量
     */
    @Query("SELECT COUNT(c) FROM Comment c WHERE ((c.email = :identifier AND c.email != '') OR (c.email = '' AND c.ipAddress = :identifier)) AND c.createdAt >= :startTime")
    long countByIdentifierAndCreatedAtAfter(@Param("identifier") String identifier, @Param("startTime") LocalDateTime startTime);

    @Query("SELECT COUNT(c) FROM Comment c WHERE c.userId = :userId AND ((c.email = :identifier AND c.email != '') OR (c.email = '' AND c.ipAddress = :identifier)) AND c.createdAt >= :startTime")
    long countByUserIdAndIdentifierAndCreatedAtAfter(@Param("userId") Long userId, @Param("identifier") String identifier, @Param("startTime") LocalDateTime startTime);

    /**
     * 检查用户是否已经对指定评论回复过（使用标识符）
     */
    @Query("SELECT COUNT(c) > 0 FROM Comment c WHERE c.parentId = :parentId AND ((c.email = :identifier AND c.email != '') OR (c.email = '' AND c.ipAddress = :identifier)) AND c.deleted = false")
    boolean hasUserRepliedToComment(@Param("parentId") Long parentId, @Param("identifier") String identifier);

    @Query("SELECT COUNT(c) > 0 FROM Comment c WHERE c.userId = :userId AND c.parentId = :parentId AND ((c.email = :identifier AND c.email != '') OR (c.email = '' AND c.ipAddress = :identifier)) AND c.deleted = false")
    boolean hasUserRepliedToComment(@Param("userId") Long userId, @Param("parentId") Long parentId, @Param("identifier") String identifier);

    /**
     * 检查用户今天是否已经对指定相册发表过顶级评论
     */
    @Query("SELECT COUNT(c) > 0 FROM Comment c WHERE c.albumId = :albumId AND ((c.email = :identifier AND c.email != '') OR (c.email = '' AND c.ipAddress = :identifier)) AND c.parentId IS NULL AND c.deleted = false AND DATE(c.createdAt) = DATE(CURRENT_DATE)")
    boolean hasUserCommentedOnAlbumToday(@Param("albumId") Long albumId, @Param("identifier") String identifier);

    @Query("SELECT COUNT(c) > 0 FROM Comment c WHERE c.userId = :userId AND c.albumId = :albumId AND ((c.email = :identifier AND c.email != '') OR (c.email = '' AND c.ipAddress = :identifier)) AND c.parentId IS NULL AND c.deleted = false AND DATE(c.createdAt) = DATE(CURRENT_DATE)")
    boolean hasUserCommentedOnAlbumToday(@Param("userId") Long userId, @Param("albumId") Long albumId, @Param("identifier") String identifier);

    /**
     * 检查用户是否已经对指定相册发表过顶级评论（未删除）
     */
    boolean existsByAlbumIdAndEmailAndParentIdIsNullAndDeletedFalse(Long albumId, String email);

    List<Comment> findByUserIdIsNull();

}
