package com.photoexhibition.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class TwilioSmsService implements SmsSender {

    private static final String DEFAULT_ENDPOINT = "https://api.twilio.com/2010-04-01";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final UserPathService userPathService;

    @Override
    public SmsProviderType getProviderType() {
        return SmsProviderType.TWILIO;
    }

    @Override
    public SmsSenderService.SmsSendResult sendLoginCode(String phone, String code, SmsConfigService.SmsResolvedSettings settings) {
        validateConfig(settings);
        try {
            String accountSid = settings.getAccessKeyId().trim();
            String endpointBase = blankToDefault(settings.getEndpoint(), DEFAULT_ENDPOINT);
            String endpoint = endpointBase.endsWith("/")
                ? endpointBase + "Accounts/" + accountSid + "/Messages.json"
                : endpointBase + "/Accounts/" + accountSid + "/Messages.json";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Authorization", "Basic " + Base64.getEncoder()
                .encodeToString((accountSid + ":" + settings.getAccessKeySecret()).getBytes(StandardCharsets.UTF_8)));

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("To", normalizePhone(phone, settings.getRegionId()));
            if (notBlank(settings.getTemplateCode())) {
                body.add("MessagingServiceSid", settings.getTemplateCode().trim());
            } else {
                body.add("From", settings.getSignName().trim());
            }
            body.add("Body", buildMessageBody(code, settings));

            ResponseEntity<String> response = restTemplate.postForEntity(endpoint, new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            String sid = root.path("sid").asText(null);
            String errorMessage = root.path("message").asText(null);
            if (sid == null || sid.isBlank()) {
                throw new RuntimeException(errorMessage == null || errorMessage.isBlank() ? "Twilio 短信发送失败" : errorMessage);
            }
            return SmsSenderService.SmsSendResult.builder()
                .success(true)
                .providerMessageId(sid)
                .rawResponse(response.getBody())
                .build();
        } catch (Exception e) {
            throw new RuntimeException("短信发送失败: " + userPathService.sanitizeVisibleText(e.getMessage()), e);
        }
    }

    private void validateConfig(SmsConfigService.SmsResolvedSettings settings) {
        if (!notBlank(settings.getAccessKeyId()) ||
            !notBlank(settings.getAccessKeySecret()) ||
            (!notBlank(settings.getSignName()) && !notBlank(settings.getTemplateCode()))) {
            throw new RuntimeException("Twilio 短信配置不完整");
        }
    }

    private String normalizePhone(String phone, String regionId) {
        String normalized = phone == null ? "" : phone.trim();
        if (normalized.startsWith("+")) {
            return normalized;
        }
        String countryCode = notBlank(regionId) ? regionId.trim() : "+86";
        if (!countryCode.startsWith("+")) {
            countryCode = "+" + countryCode;
        }
        return countryCode + normalized;
    }

    private String buildMessageBody(String code, SmsConfigService.SmsResolvedSettings settings) {
        String signName = notBlank(settings.getSignName()) ? settings.getSignName().trim() : "Photo Exhibition";
        return "【" + signName + "】验证码：" + code + "，" + settings.getCodeExpireMinutes() + " 分钟内有效。";
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String blankToDefault(String value, String fallback) {
        return notBlank(value) ? value.trim() : fallback;
    }
}
