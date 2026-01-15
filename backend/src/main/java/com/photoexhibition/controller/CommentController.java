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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
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
            // 从请求中获取IP地址
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            String ipAddress = "127.0.0.1"; // 默认IP
            if (attrs != null) {
                HttpServletRequest httpRequest = attrs.getRequest();
                if (httpRequest != null) {
                    ipAddress = getClientIpAddress(httpRequest);
                }
            }

            // 设置IP地址到请求中
            request.setIpAddress(ipAddress);

            // 检查评论内容
            validateCommentContent(request.getContent());

            // 检查频率限制
            commentService.validateCommentRateLimit(request.getEmail(), ipAddress);

            // 如果是顶级评论，检查用户今天是否已经发表过评论
            if (request.getParentId() == null) {
                if (commentService.hasUserCommentedOnAlbumToday(request.getAlbumId(), request.getEmail(), ipAddress)) {
                    return ResponseEntity.badRequest().build(); // 用户今天已经发表过评论
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

    /**
     * 检查用户是否已经对指定评论回复过
     */
    @GetMapping("/{parentId}/has-replied")
    public ResponseEntity<Boolean> hasUserRepliedToComment(
            @PathVariable Long parentId,
            @RequestParam(required = false) String email,
            HttpServletRequest httpRequest) {
        String ipAddress = getClientIpAddress(httpRequest);
        boolean hasReplied = commentService.hasUserRepliedToComment(parentId, email, ipAddress);
        return ResponseEntity.ok(hasReplied);
    }

    @PostMapping("/batch-has-replied")
    public ResponseEntity<Map<Long, Boolean>> batchHasUserRepliedToComments(
            @RequestBody List<Long> commentIds,
            @RequestParam(required = false) String email,
            HttpServletRequest httpRequest) {
        String ipAddress = getClientIpAddress(httpRequest);
        Map<Long, Boolean> results = commentService.batchHasUserRepliedToComments(commentIds, email, ipAddress);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/albums/{albumId}/has-commented-today")
    public ResponseEntity<Boolean> hasUserCommentedOnAlbumToday(
            @PathVariable Long albumId,
            @RequestParam(required = false) String email,
            HttpServletRequest httpRequest) {
        String ipAddress = getClientIpAddress(httpRequest);
        boolean hasCommented = commentService.hasUserCommentedOnAlbumToday(albumId, email, ipAddress);
        return ResponseEntity.ok(hasCommented);
    }

    /**
     * 获取客户端真实IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }

        // 如果有多个IP，取第一个
        if (ipAddress != null && ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0].trim();
        }

        return ipAddress;
    }

    /**
     * 验证评论内容
     */
    private void validateCommentContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return; // 内容验证由@NotBlank处理
        }

        String lowerContent = content.toLowerCase();

        // 敏感词列表
        String[] sensitiveWords = {
            "广告", "推广", "营销", "宣传", "代理", "招商", "兼职", "赚钱", "投资", "理财",
            "赌博", "彩票", "色情", "裸露", "成人", "约炮", "援交", "性服务",
            "毒品", "毒品", "贩毒", "吸毒", "冰毒", "摇头丸",
            "政治", "共产党", "国民党", "习近平", "毛泽东", "江泽民", "胡锦涛", "温家宝",
            "fuck", "shit", "bitch", "damn", "asshole", "bastard",
            "共产党", "共匪", "匪徒", "叛徒", "汉奸", "卖国贼",
            "台独", "港独", "疆独", "藏独", "分裂", "恐怖主义"
        };

        // 检查敏感词
        for (String word : sensitiveWords) {
            if (lowerContent.contains(word.toLowerCase())) {
                throw new IllegalArgumentException("评论包含不当内容，请文明发言");
            }
        }

        // 检查网址
        if (lowerContent.matches(".*https?://.*") ||
            lowerContent.matches(".*www\\..*\\..*") ||
            lowerContent.matches(".*\\.com.*") ||
            lowerContent.matches(".*\\.cn.*") ||
            lowerContent.matches(".*\\.net.*") ||
            lowerContent.matches(".*\\.org.*")) {
            throw new IllegalArgumentException("评论不允许包含网址链接");
        }

        // 检查电话号码
        if (lowerContent.matches(".*1[3-9]\\d{9}.*")) {
            throw new IllegalArgumentException("评论不允许包含联系方式");
        }

        // 检查QQ号
        if (lowerContent.matches(".*[Qq][Qq][:：]?\\s*\\d{5,11}.*")) {
            throw new IllegalArgumentException("评论不允许包含联系方式");
        }

        // 检查微信号
        if (lowerContent.matches(".*[Ww][Xx][:：]?\\s*[a-zA-Z0-9_-]{6,20}.*") ||
            lowerContent.contains("微信") && lowerContent.matches(".*\\d{6,20}.*")) {
            throw new IllegalArgumentException("评论不允许包含联系方式");
        }
    }
}
