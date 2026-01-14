package com.photoexhibition.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CommentDTO {
    private Long id;
    private Long albumId;
    private Long parentId;
    private String nickname;
    private String email;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 回复列表（仅在查询顶级评论时包含）
    private List<CommentDTO> replies;
}
