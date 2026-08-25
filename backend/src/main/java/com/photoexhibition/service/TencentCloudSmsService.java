package com.photoexhibition.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TencentCloudSmsService implements SmsSender {

    private static final String DEFAULT_ENDPOINT = "https://sms.tencentcloudapi.com";
    private static final String SERVICE = "sms";
    private static final String HOST = "sms.tencentcloudapi.com";
    private static final String ACTION = "SendSms";
    private static final String VERSION = "2021-01-11";
    private static final String ALGORITHM = "TC3-HMAC-SHA256";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final UserPathService userPathService;

    @Override
    public SmsProviderType getProviderType() {
        return SmsProviderType.TENCENT_CLOUD;
    }

    @Override
    public SmsSenderService.SmsSendResult sendLoginCode(String phone,
                                                        String code,
                                                        SmsConfigService.SmsResolvedSettings settings) {
        validateConfig(settings);
        try {
            String endpoint = isBlank(settings.getEndpoint()) ? DEFAULT_ENDPOINT : settings.getEndpoint().trim();
            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
            long timestamp = now.toEpochSecond();
            String date = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            String payload = objectMapper.writeValueAsString(Map.of(
                "SmsSdkAppId", settings.getSdkAppId(),
                "SignName", settings.getSignName(),
                "TemplateId", settings.getTemplateCode(),
                "TemplateParamSet", List.of(code),
                "PhoneNumberSet", List.of("+86" + phone),
                "SessionContext", "login"
            ));

            String hashedRequestPayload = sha256Hex(payload);
            String canonicalRequest = "POST\n/\n\n" +
                "content-type:application/json; charset=utf-8\n" +
                "host:" + HOST + "\n" +
                "x-tc-action:" + ACTION.toLowerCase() + "\n\n" +
                "content-type;host;x-tc-action\n" +
                hashedRequestPayload;

            String credentialScope = date + "/" + SERVICE + "/tc3_request";
            String stringToSign = ALGORITHM + "\n" +
                timestamp + "\n" +
                credentialScope + "\n" +
                sha256Hex(canonicalRequest);

            byte[] secretDate = hmacSha256(("TC3" + settings.getAccessKeySecret()).getBytes(StandardCharsets.UTF_8), date);
            byte[] secretService = hmacSha256(secretDate, SERVICE);
            byte[] secretSigning = hmacSha256(secretService, "tc3_request");
            String signature = toHex(hmacSha256(secretSigning, stringToSign));

            String authorization = ALGORITHM + " Credential=" + settings.getAccessKeyId() + "/" + credentialScope +
                ", SignedHeaders=content-type;host;x-tc-action, Signature=" + signature;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/json; charset=utf-8"));
            headers.set("Host", HOST);
            headers.set("X-TC-Action", ACTION);
            headers.set("X-TC-Version", VERSION);
            headers.set("X-TC-Region", defaultString(settings.getRegionId(), "ap-guangzhou"));
            headers.set("X-TC-Timestamp", String.valueOf(timestamp));
            headers.set("Authorization", authorization);

            ResponseEntity<String> response = restTemplate.postForEntity(endpoint, new HttpEntity<>(payload, headers), String.class);
            String body = response.getBody();
            JsonNode root = objectMapper.readTree(body);
            JsonNode errorNode = root.path("Response").path("Error");
            if (!errorNode.isMissingNode() && !errorNode.isEmpty()) {
                throw new RuntimeException(errorNode.path("Message").asText("短信发送失败"));
            }

            return SmsSenderService.SmsSendResult.builder()
                .success(true)
                .providerMessageId(root.path("Response").path("SendStatusSet").path(0).path("SerialNo").asText(null))
                .rawResponse(body)
                .build();
        } catch (Exception e) {
            throw new RuntimeException("短信发送失败: " + userPathService.sanitizeVisibleText(e.getMessage()), e);
        }
    }

    private void validateConfig(SmsConfigService.SmsResolvedSettings settings) {
        if (isBlank(settings.getAccessKeyId()) ||
            isBlank(settings.getAccessKeySecret()) ||
            isBlank(settings.getSignName()) ||
            isBlank(settings.getTemplateCode()) ||
            isBlank(settings.getSdkAppId())) {
            throw new RuntimeException("腾讯云短信配置不完整");
        }
    }

    private byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return toHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String defaultString(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }
}
