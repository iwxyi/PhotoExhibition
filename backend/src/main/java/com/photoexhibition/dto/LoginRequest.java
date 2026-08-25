package com.photoexhibition.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;

    private String password;

    private String code;

    private String loginType;
}
