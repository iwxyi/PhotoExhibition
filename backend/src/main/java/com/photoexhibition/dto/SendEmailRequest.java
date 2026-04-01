package com.photoexhibition.dto;

import lombok.Data;

@Data
public class SendEmailRequest {
    private String recipient;
    private String subject;
    private String content;
    private Boolean html;
}
