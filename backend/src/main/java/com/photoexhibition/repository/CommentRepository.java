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

    /**
     * 根据相册ID查询顶级评论（parentId为null）
     */
    Page<Comment> findByAlbumIdAndParentIdIsNullOrderByCreatedAtDesc(Long albumId, Pageable pageable);

    /**
     * 根据父评论ID查询回复
     */
    List<Comment> findByParentIdOrderByCreatedAtAsc(Long parentId);

    /**
     * 根据相册ID查询所有评论
     */
    List<Comment> findByAlbumIdOrderByCreatedAtDesc(Long albumId);

    /**
     * 根据邮箱查询用户在指定时间范围内的评论数量
     */
    @Query("SELECT COUNT(c) FROM Comment c WHERE c.email = :email AND c.createdAt >= :startTime")
    long countByEmailAndCreatedAtAfter(@Param("email") String email, @Param("startTime") LocalDateTime startTime);

    /**
     * 检查用户是否已经对指定相册发表过顶级评论
     */
    boolean existsByAlbumIdAndEmailAndParentIdIsNull(Long albumId, String email);
}
