package com.photoexhibition.dto;

import lombok.Data;

@Data
public class ResetPasswordByEmailRequest {
    private String email;
    private String code;
    private String newPassword;
    private String confirmPassword;
}
