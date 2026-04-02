package com.photoexhibition.dto;

import lombok.Data;

import java.util.Map;

@Data
public class SendEmailRequest {
    private String recipient;
    private String subject;
    private String content;
    private Boolean html;
    private String templateKey;
    private Map<String, Object> variables;
}
