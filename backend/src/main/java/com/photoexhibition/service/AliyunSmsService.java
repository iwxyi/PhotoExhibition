package com.photoexhibition.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AliyunSmsService implements SmsSender {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final UserPathService userPathService;

    @Override
    public SmsProviderType getProviderType() {
        return SmsProviderType.ALIYUN;
    }

    @Override
    public SmsSenderService.SmsSendResult sendLoginCode(String phone, String code, SmsConfigService.SmsResolvedSettings settings) {
        validateConfig(settings);
        try {
            String templateParam = objectMapper.writeValueAsString(Map.of(settings.getTemplateParamName(), code));

            Map<String, String> params = new TreeMap<>();
            params.put("AccessKeyId", settings.getAccessKeyId());
            params.put("Action", "SendSms");
            params.put("Format", "JSON");
            params.put("PhoneNumbers", phone);
            params.put("RegionId", settings.getRegionId());
            params.put("SignName", settings.getSignName());
            params.put("SignatureMethod", "HMAC-SHA1");
            params.put("SignatureNonce", UUID.randomUUID().toString());
            params.put("SignatureVersion", "1.0");
            params.put("TemplateCode", settings.getTemplateCode());
            params.put("TemplateParam", templateParam);
            params.put("Timestamp", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
            params.put("Version", "2017-05-25");

            String signature = sign(params, settings.getAccessKeySecret());
            String query = params.entrySet().stream()
                .map(entry -> percentEncode(entry.getKey()) + "=" + percentEncode(entry.getValue()))
                .collect(Collectors.joining("&"));
            String endpoint = normalizeEndpoint(settings.getEndpoint());
            String url = endpoint + "?" + query + "&Signature=" + percentEncode(signature);
            String response = restTemplate.getForObject(URI.create(url), String.class);
            JsonNode root = objectMapper.readTree(response);
            String responseCode = root.path("Code").asText();
            if (!"OK".equalsIgnoreCase(responseCode)) {
                throw new RuntimeException(root.path("Message").asText("短信发送失败"));
            }
            return SmsSenderService.SmsSendResult.builder()
                .success(true)
                .providerMessageId(root.path("BizId").asText(null))
                .rawResponse(response)
                .build();
        } catch (Exception e) {
            throw new RuntimeException("短信发送失败: " + userPathService.sanitizeVisibleText(e.getMessage()), e);
        }
    }

    private void validateConfig(SmsConfigService.SmsResolvedSettings settings) {
        if (isBlank(settings.getAccessKeyId()) ||
            isBlank(settings.getAccessKeySecret()) ||
            isBlank(settings.getSignName()) ||
            isBlank(settings.getTemplateCode())) {
            throw new RuntimeException("阿里云短信配置不完整");
        }
    }

    private String sign(Map<String, String> params, String accessKeySecret) throws Exception {
        String canonicalizedQueryString = params.entrySet().stream()
            .map(entry -> percentEncode(entry.getKey()) + "=" + percentEncode(entry.getValue()))
            .collect(Collectors.joining("&"));
        String stringToSign = "GET&%2F&" + percentEncode(canonicalizedQueryString);

        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec((accessKeySecret + "&").getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] signatureBytes = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signatureBytes);
    }

    private String percentEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
        } catch (Exception e) {
            throw new RuntimeException("URL编码失败", e);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalizeEndpoint(String endpoint) {
        if (isBlank(endpoint)) {
            return "https://dysmsapi.aliyuncs.com/";
        }
        return endpoint.trim();
    }

}
