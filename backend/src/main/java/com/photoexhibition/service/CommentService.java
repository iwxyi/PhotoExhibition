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
import java.util.Map;
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
        // 验证邮箱格式（如果提供的话）
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            if (!request.getEmail().contains("@")) {
                throw new IllegalArgumentException("邮箱格式不正确");
            }
        }

        Comment comment = new Comment();
        comment.setAlbumId(request.getAlbumId());
        comment.setParentId(request.getParentId() != null ? request.getParentId() : null);
        comment.setNickname(request.getNickname());
        comment.setEmail(request.getEmail() != null ? request.getEmail().trim() : "");
        comment.setContent(request.getContent());
        comment.setIpAddress(request.getIpAddress());

        Comment savedComment = commentRepository.save(comment);
        return convertToDTO(savedComment);
    }

    /**
     * 删除评论（标记删除）
     */
    @Transactional
    public void deleteComment(Long commentId, String email) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("评论不存在"));

        // 验证评论归属（只有评论作者可以删除自己的评论）
        if (!comment.getEmail().equals(email)) {
            throw new RuntimeException("无权删除此评论");
        }

        // 递归标记删除评论及其所有回复
        markCommentAsDeleted(comment);
    }

    /**
     * 递归标记评论及其回复为删除状态
     */
    private void markCommentAsDeleted(Comment comment) {
        // 标记当前评论为删除状态
        comment.setDeleted(true);
        commentRepository.save(comment);

        // 递归标记所有回复评论为删除状态
        List<Comment> replies = commentRepository.findByParentIdAndDeletedFalseOrderByCreatedAtAsc(comment.getId());
        for (Comment reply : replies) {
            markCommentAsDeleted(reply);
        }
    }

    /**
     * 获取相册的顶级评论（分页）
     */
    public Page<CommentDTO> getAlbumComments(Long albumId, Pageable pageable) {
        Page<Comment> comments = commentRepository.findByAlbumIdAndParentIdIsNullAndDeletedFalseOrderByCreatedAtDesc(albumId, pageable);
        return comments.map(this::convertToDTOWithReplies);
    }

    /**
     * 获取评论的回复
     */
    public List<CommentDTO> getCommentReplies(Long parentId) {
        List<Comment> replies = commentRepository.findByParentIdAndDeletedFalseOrderByCreatedAtAsc(parentId);
        return replies.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 检查用户是否可以发表评论（频率限制）
     */
    public void validateCommentRateLimit(String email, String ipAddress) {
        LocalDateTime now = LocalDateTime.now();

        // 检查IP地址频率限制
        if (ipAddress != null && !ipAddress.trim().isEmpty()) {
            long ipMinuteCount = commentRepository.countByIdentifierAndCreatedAtAfter(ipAddress.trim(), now.minusMinutes(1));
            if (ipMinuteCount >= 3) {
                throw new RuntimeException("评论过于频繁，请稍后再试（每分钟最多3条）");
            }

            long ipHourCount = commentRepository.countByIdentifierAndCreatedAtAfter(ipAddress.trim(), now.minusHours(1));
            if (ipHourCount >= 60) {
                throw new RuntimeException("评论过于频繁，请稍后再试（每小时最多60条）");
            }

            long ipDayCount = commentRepository.countByIdentifierAndCreatedAtAfter(ipAddress.trim(), now.minusDays(1));
            if (ipDayCount >= 200) {
                throw new RuntimeException("评论过于频繁，请稍后再试（每天最多200条）");
            }
        }

        // 检查邮箱频率限制（如果提供了邮箱）
        if (email != null && !email.trim().isEmpty()) {
            long emailMinuteCount = commentRepository.countByIdentifierAndCreatedAtAfter(email.trim(), now.minusMinutes(1));
            if (emailMinuteCount >= 3) {
                throw new RuntimeException("评论过于频繁，请稍后再试（每分钟最多3条）");
            }

            long emailHourCount = commentRepository.countByIdentifierAndCreatedAtAfter(email.trim(), now.minusHours(1));
            if (emailHourCount >= 60) {
                throw new RuntimeException("评论过于频繁，请稍后再试（每小时最多60条）");
            }

            long emailDayCount = commentRepository.countByIdentifierAndCreatedAtAfter(email.trim(), now.minusDays(1));
            if (emailDayCount >= 200) {
                throw new RuntimeException("评论过于频繁，请稍后再试（每天最多200条）");
            }
        }
    }


    /**
     * 获取相册评论总数（包括回复）
     */
    public long getAlbumCommentCount(Long albumId) {
        List<Comment> comments = commentRepository.findByAlbumIdAndDeletedFalseOrderByCreatedAtDesc(albumId);
        return comments.size();
    }

    /**
     * 检查用户是否已经对指定评论回复过
     */
    public boolean hasUserRepliedToComment(Long parentId, String email, String ipAddress) {
        // 使用邮箱或IP进行检查
        String identifier = (email != null && !email.trim().isEmpty()) ? email.trim() : ipAddress;

        if (identifier == null || identifier.trim().isEmpty()) {
            return false;
        }

        // 检查用户是否已经对这个评论发表过回复
        return commentRepository.hasUserRepliedToComment(parentId, identifier);
    }

    public Map<Long, Boolean> batchHasUserRepliedToComments(List<Long> commentIds, String email, String ipAddress) {
        // 使用邮箱或IP进行检查
        String identifier = (email != null && !email.trim().isEmpty()) ? email.trim() : ipAddress;

        if (identifier == null || identifier.trim().isEmpty()) {
            // 如果没有有效的标识符，所有结果都返回false
            return commentIds.stream()
                    .collect(Collectors.toMap(id -> id, id -> false));
        }

        // 批量检查所有评论的回复状态
        return commentIds.stream()
                .collect(Collectors.toMap(
                    id -> id,
                    id -> commentRepository.hasUserRepliedToComment(id, identifier)
                ));
    }

    /**
     * 检查用户今天是否已经对指定相册发表过顶级评论
     */
    public boolean hasUserCommentedOnAlbumToday(Long albumId, String email, String ipAddress) {
        // 使用邮箱或IP进行检查
        String identifier = (email != null && !email.trim().isEmpty()) ? email.trim() : ipAddress;

        if (identifier == null || identifier.trim().isEmpty()) {
            return false;
        }

        return commentRepository.hasUserCommentedOnAlbumToday(albumId, identifier);
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
        dto.setDeleted(comment.getDeleted());
        dto.setIpAddress(comment.getIpAddress());
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
