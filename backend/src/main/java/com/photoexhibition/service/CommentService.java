package com.photoexhibition.service;

import com.photoexhibition.dto.CommentDTO;
import com.photoexhibition.dto.CommentRequest;
import com.photoexhibition.entity.Comment;
import com.photoexhibition.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;

    /**
     * 创建评论
     */
    @Transactional
    public CommentDTO createComment(CommentRequest request) {
        Comment comment = new Comment();
        comment.setAlbumId(request.getAlbumId());
        comment.setParentId(request.getParentId() != null ? request.getParentId() : null);
        comment.setNickname(request.getNickname());
        comment.setEmail(request.getEmail());
        comment.setContent(request.getContent());

        Comment savedComment = commentRepository.save(comment);
        return convertToDTO(savedComment);
    }

    /**
     * 删除评论
     */
    @Transactional
    public void deleteComment(Long commentId, String email) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("评论不存在"));

        // 验证评论归属（只有评论作者可以删除自己的评论）
        if (!comment.getEmail().equals(email)) {
            throw new RuntimeException("无权删除此评论");
        }

        commentRepository.delete(comment);
    }

    /**
     * 获取相册的顶级评论（分页）
     */
    public Page<CommentDTO> getAlbumComments(Long albumId, Pageable pageable) {
        Page<Comment> comments = commentRepository.findByAlbumIdAndParentIdIsNullOrderByCreatedAtDesc(albumId, pageable);
        return comments.map(this::convertToDTOWithReplies);
    }

    /**
     * 获取评论的回复
     */
    public List<CommentDTO> getCommentReplies(Long parentId) {
        List<Comment> replies = commentRepository.findByParentIdOrderByCreatedAtAsc(parentId);
        return replies.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 检查用户是否可以发表评论（频率限制）
     */
    public void validateCommentRateLimit(String email) {
        LocalDateTime now = LocalDateTime.now();

        // 检查一分钟内的评论数
        long minuteCount = commentRepository.countByEmailAndCreatedAtAfter(email, now.minusMinutes(1));
        if (minuteCount >= 3) {
            throw new RuntimeException("评论过于频繁，请稍后再试（每分钟最多3条）");
        }

        // 检查一小时内的评论数
        long hourCount = commentRepository.countByEmailAndCreatedAtAfter(email, now.minusHours(1));
        if (hourCount >= 60) {
            throw new RuntimeException("评论过于频繁，请稍后再试（每小时最多60条）");
        }

        // 检查一天内的评论数
        long dayCount = commentRepository.countByEmailAndCreatedAtAfter(email, now.minusDays(1));
        if (dayCount >= 200) {
            throw new RuntimeException("评论过于频繁，请稍后再试（每天最多200条）");
        }
    }

    /**
     * 检查用户是否已经对指定相册发表过顶级评论
     */
    public boolean hasUserCommentedOnAlbum(Long albumId, String email) {
        return commentRepository.existsByAlbumIdAndEmailAndParentIdIsNull(albumId, email);
    }

    /**
     * 获取相册评论总数（包括回复）
     */
    public long getAlbumCommentCount(Long albumId) {
        List<Comment> comments = commentRepository.findByAlbumIdOrderByCreatedAtDesc(albumId);
        return comments.size();
    }

    /**
     * 转换为DTO（不包含回复）
     */
    private CommentDTO convertToDTO(Comment comment) {
        CommentDTO dto = new CommentDTO();
        dto.setId(comment.getId());
        dto.setAlbumId(comment.getAlbumId());
        dto.setParentId(comment.getParentId());
        dto.setNickname(comment.getNickname());
        dto.setEmail(comment.getEmail());
        dto.setContent(comment.getContent());
        dto.setCreatedAt(comment.getCreatedAt());
        dto.setUpdatedAt(comment.getUpdatedAt());
        return dto;
    }

    /**
     * 转换为DTO（包含回复）
     */
    private CommentDTO convertToDTOWithReplies(Comment comment) {
        CommentDTO dto = convertToDTO(comment);
        // 加载回复
        List<CommentDTO> replies = getCommentReplies(comment.getId());
        dto.setReplies(replies);
        return dto;
    }
}
