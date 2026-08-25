package com.photoexhibition.dto;

import lombok.Data;

@Data
public class ResetPasswordByPhoneRequest {
    private String phone;
    private String code;
    private String newPassword;
    private String confirmPassword;
}
