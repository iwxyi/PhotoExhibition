package com.photoexhibition.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.service.PaymentCallbackService;
import com.photoexhibition.service.PaymentInitiationService;
import com.photoexhibition.service.UserPathService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private static final Pattern EMBEDDED_PATH_PATTERN =
        Pattern.compile("(storage://[^\\s,;]+|[A-Za-z]:\\\\[^\\s,;]+|/(?:[^\\s,;])+)");

    private final PaymentCallbackService paymentCallbackService;
    private final PaymentInitiationService paymentInitiationService;
    private final UserPathService userPathService;
    private final ObjectMapper objectMapper;

    @PostMapping("/orders/{orderId}/initiate")
    public ResponseEntity<?> initiate(@PathVariable Long orderId) {
        try {
            return ResponseEntity.ok(paymentInitiationService.initiateByOrderId(orderId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", sanitizeErrorMessage(e.getMessage(), "支付发起失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "支付发起失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    @PostMapping("/notify/{providerType}")
    public ResponseEntity<?> notify(@PathVariable String providerType,
                                    @RequestBody(required = false) String rawBody,
                                    @RequestParam Map<String, String> requestParams,
                                    @RequestHeader Map<String, String> headers,
                                    HttpServletRequest request) {
        try {
            return ResponseEntity.ok(paymentCallbackService.handleNotify(
                providerType,
                mergeNotifyPayload(resolveBodyPayload(rawBody, request), requestParams, headers, rawBody)
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", sanitizeErrorMessage(e.getMessage(), "支付回调处理失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "支付回调处理失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    @GetMapping("/return/{providerType}")
    public ResponseEntity<?> paymentReturn(@PathVariable String providerType,
                                           @RequestParam Map<String, String> queryParams,
                                           HttpServletRequest request) {
        try {
            if (prefersHtml(request, queryParams)) {
                return ResponseEntity.status(302)
                    .header(HttpHeaders.LOCATION, buildFrontendResultUrl(providerType, queryParams).toString())
                    .build();
            }
            return ResponseEntity.ok(paymentCallbackService.handleReturn(providerType, queryParams));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", sanitizeErrorMessage(e.getMessage(), "支付返回处理失败")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "支付返回处理失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常")));
        }
    }

    private Map<String, Object> mergeNotifyPayload(Map<String, Object> payload,
                                                   Map<String, String> requestParams,
                                                   Map<String, String> headers,
                                                   String rawBody) {
        Map<String, Object> mergedPayload = new LinkedHashMap<>();
        if (requestParams != null && !requestParams.isEmpty()) {
            mergedPayload.putAll(requestParams);
        }
        if (payload != null && !payload.isEmpty()) {
            mergedPayload.putAll(payload);
        }
        if (headers != null && !headers.isEmpty()) {
            mergedPayload.put("_headers", headers);
        }
        if (rawBody != null && !rawBody.isBlank()) {
            mergedPayload.put("rawBody", rawBody);
        }
        return mergedPayload;
    }

    private Map<String, Object> parseJsonBody(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(rawBody, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("JSON 回调体解析失败");
        }
    }

    private Map<String, Object> resolveBodyPayload(String rawBody, HttpServletRequest request) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        String trimmed = rawBody.trim();
        String contentType = request == null ? null : request.getContentType();
        boolean jsonLike = (trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"));
        boolean jsonContentType = contentType != null && contentType.toLowerCase().contains("json");
        if (jsonLike || jsonContentType) {
            return parseJsonBody(rawBody);
        }
        return null;
    }

    private boolean prefersHtml(HttpServletRequest request, Map<String, String> queryParams) {
        if ("json".equalsIgnoreCase(queryParams.get("format"))) {
            return false;
        }
        if ("true".equalsIgnoreCase(queryParams.get("redirect"))) {
            return true;
        }
        String accept = request == null ? null : request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
    }

    private URI buildFrontendResultUrl(String providerType, Map<String, String> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/vip/result")
            .queryParam("providerType", providerType);
        if (queryParams != null) {
            queryParams.forEach((key, value) -> {
                if (key == null || key.isBlank() || value == null || "redirect".equalsIgnoreCase(key) || "format".equalsIgnoreCase(key)) {
                    return;
                }
                builder.queryParam(key, value);
            });
        }
        return builder.build(true).toUri();
    }

    private String sanitizeErrorMessage(String message, String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }
        Matcher matcher = EMBEDDED_PATH_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        boolean replaced = false;
        while (matcher.find()) {
            String candidate = matcher.group(1);
            String sanitizedCandidate = userPathService.toDisplayPath(candidate, true);
            if (!candidate.equals(sanitizedCandidate)) {
                replaced = true;
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(sanitizedCandidate));
        }
        matcher.appendTail(buffer);
        return replaced ? buffer.toString() : message;
    }
}
