package com.photoexhibition.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {
    private String token;
    private Long userId;
    private String slug;
    private String username;
    private String phone;
    private String email;
    private String nickname;
    private Boolean phoneVerified;
    private Boolean emailVerified;
    private String projectNameZh;
    private String projectNameEn;
    private String avatarPath;
    private String role;
    private Boolean multiUserEnabled;
    private Long currentVipPlanId;
    private String currentVipPlanName;
    private String currentVipPlanCode;
    private String vipExpireAt;
    private Long effectiveStorageQuotaBytes;
    private Long storageUsedBytes;
    private String message;
}
