package com.photoexhibition.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PublicUserProfileResponse {
    private Long userId;
    private String slug;
    private String username;
    private String nickname;
    private String projectNameZh;
    private String projectNameEn;
    private String avatarPath;
}
