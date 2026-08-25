package com.photoexhibition.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WebhookSmsService implements SmsSender {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final UserPathService userPathService;

    @Override
    public SmsProviderType getProviderType() {
        return SmsProviderType.HTTP_WEBHOOK;
    }

    @Override
    public SmsSenderService.SmsSendResult sendLoginCode(String phone, String code, SmsConfigService.SmsResolvedSettings settings) {
        if (settings.getEndpoint() == null || settings.getEndpoint().trim().isEmpty()) {
            throw new RuntimeException("Webhook 短信未配置 endpoint");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (settings.getAccessKeyId() != null && !settings.getAccessKeyId().isBlank()) {
                String token = Base64.getEncoder().encodeToString(
                    (settings.getAccessKeyId().trim() + ":" + safeValue(settings.getAccessKeySecret())).getBytes(StandardCharsets.UTF_8)
                );
                headers.set("Authorization", "Basic " + token);
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("phone", phone);
            payload.put("code", code);
            payload.put("signName", settings.getSignName());
            payload.put("templateCode", settings.getTemplateCode());
            payload.put("templateParamName", settings.getTemplateParamName());
            payload.put("sdkAppId", settings.getSdkAppId());
            payload.put("regionId", settings.getRegionId());
            payload.put("expireMinutes", settings.getCodeExpireMinutes());
            payload.put("providerType", settings.getProviderType() != null ? settings.getProviderType().name() : SmsProviderType.HTTP_WEBHOOK.name());

            ResponseEntity<String> response = restTemplate.postForEntity(
                settings.getEndpoint().trim(),
                new HttpEntity<>(payload, headers),
                String.class
            );
            return SmsSenderService.SmsSendResult.builder()
                .success(response.getStatusCode().is2xxSuccessful())
                .providerMessageId(response.getHeaders().getFirst("X-Message-Id"))
                .rawResponse(response.getBody() != null ? response.getBody() : objectMapper.writeValueAsString(payload))
                .build();
        } catch (Exception e) {
            throw new RuntimeException("短信发送失败: " + userPathService.sanitizeVisibleText(e.getMessage()), e);
        }
    }

    private String safeValue(String value) {
        return value == null ? "" : value.trim();
    }
}
