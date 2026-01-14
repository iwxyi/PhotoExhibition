package com.photoexhibition.controller;

import com.photoexhibition.dto.CommentDTO;
import com.photoexhibition.dto.CommentRequest;
import com.photoexhibition.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CommentController {

    private final CommentService commentService;

    /**
     * 创建评论
     */
    @PostMapping
    public ResponseEntity<CommentDTO> createComment(@Valid @RequestBody CommentRequest request) {
        try {
            // 检查频率限制
            commentService.validateCommentRateLimit(request.getEmail());

            // 如果是顶级评论，检查用户是否已经对该相册发表过评论
            if (request.getParentId() == null) {
                if (commentService.hasUserCommentedOnAlbum(request.getAlbumId(), request.getEmail())) {
                    return ResponseEntity.badRequest().build(); // 用户已经对该相册发表过评论
                }
            }

            CommentDTO comment = commentService.createComment(request);
            return ResponseEntity.ok(comment);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 删除评论
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long id,
            @RequestParam String email) {
        try {
            commentService.deleteComment(id, email);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 获取相册的评论（分页）
     */
    @GetMapping("/albums/{albumId}")
    public ResponseEntity<Page<CommentDTO>> getAlbumComments(
            @PathVariable Long albumId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<CommentDTO> comments = commentService.getAlbumComments(albumId, pageable);
        return ResponseEntity.ok(comments);
    }

    /**
     * 获取评论的回复
     */
    @GetMapping("/{parentId}/replies")
    public ResponseEntity<java.util.List<CommentDTO>> getCommentReplies(@PathVariable Long parentId) {
        java.util.List<CommentDTO> replies = commentService.getCommentReplies(parentId);
        return ResponseEntity.ok(replies);
    }

    /**
     * 获取相册评论总数（包括回复）
     */
    @GetMapping("/albums/{albumId}/count")
    public ResponseEntity<Long> getAlbumCommentCount(@PathVariable Long albumId) {
        long count = commentService.getAlbumCommentCount(albumId);
        return ResponseEntity.ok(count);
    }
}
