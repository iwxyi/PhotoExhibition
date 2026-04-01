package com.photoexhibition.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String slug;
    private String nickname;
    private String phone;
    private String email;
    private String projectNameZh;
    private String projectNameEn;
}
