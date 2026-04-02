package com.photoexhibition.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;

import javax.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PaymentPayloadParser {

    private PaymentPayloadParser() {
    }

    static Map<String, Object> resolveBodyPayload(String rawBody,
                                                  HttpServletRequest request,
                                                  ObjectMapper objectMapper) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        String trimmed = rawBody.trim();
        String contentType = request == null ? null : request.getContentType();
        boolean jsonLike = (trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"));
        boolean jsonContentType = contentType != null && contentType.toLowerCase().contains("json");
        boolean formContentType = contentType != null && contentType.toLowerCase().contains(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        if (jsonLike || jsonContentType) {
            return parseJsonBody(rawBody, objectMapper);
        }
        if (formContentType || looksLikeQueryString(trimmed)) {
            return parseUrlEncodedBody(trimmed);
        }
        return null;
    }

    private static Map<String, Object> parseJsonBody(String rawBody, ObjectMapper objectMapper) {
        if (rawBody == null || rawBody.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(rawBody, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("JSON 回调体解析失败");
        }
    }

    private static boolean looksLikeQueryString(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return false;
        }
        return rawBody.contains("=") && !rawBody.contains("{") && !rawBody.contains("}");
    }

    private static Map<String, Object> parseUrlEncodedBody(String rawBody) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (String pair : rawBody.split("&")) {
            if (pair == null || pair.isBlank()) {
                continue;
            }
            int equalsIndex = pair.indexOf('=');
            String rawKey = equalsIndex >= 0 ? pair.substring(0, equalsIndex) : pair;
            String rawValue = equalsIndex >= 0 ? pair.substring(equalsIndex + 1) : "";
            String key = urlDecode(rawKey);
            if (key == null || key.isBlank()) {
                continue;
            }
            putNestedValue(payload, parseKeySegments(key), urlDecode(rawValue));
        }
        return payload;
    }

    private static List<String> parseKeySegments(String key) {
        List<String> segments = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char current = key.charAt(i);
            if (current == '.') {
                appendToken(segments, token);
                continue;
            }
            if (current == '[') {
                appendToken(segments, token);
                int closing = key.indexOf(']', i);
                if (closing < 0) {
                    token.append(current);
                    continue;
                }
                String bracketToken = key.substring(i + 1, closing);
                if (!bracketToken.isBlank()) {
                    segments.add(bracketToken);
                }
                i = closing;
                continue;
            }
            token.append(current);
        }
        appendToken(segments, token);
        return segments.isEmpty() ? List.of(key) : segments;
    }

    private static void appendToken(List<String> segments, StringBuilder token) {
        if (token.length() == 0) {
            return;
        }
        segments.add(token.toString());
        token.setLength(0);
    }

    @SuppressWarnings("unchecked")
    private static void putNestedValue(Map<String, Object> payload, List<String> segments, String value) {
        if (segments == null || segments.isEmpty()) {
            return;
        }
        Object current = payload;
        for (int i = 0; i < segments.size() - 1; i++) {
            String segment = segments.get(i);
            String nextSegment = segments.get(i + 1);
            boolean nextIsIndex = isNumeric(nextSegment);
            if (current instanceof Map<?, ?>) {
                Map<String, Object> map = (Map<String, Object>) current;
                Object child = map.get(segment);
                if (!isExpectedContainer(child, nextIsIndex)) {
                    child = nextIsIndex ? new ArrayList<>() : new LinkedHashMap<String, Object>();
                    map.put(segment, child);
                }
                current = child;
                continue;
            }
            if (current instanceof List<?> && isNumeric(segment)) {
                List<Object> objectList = (List<Object>) current;
                int index = Integer.parseInt(segment);
                ensureListSize(objectList, index + 1);
                Object child = objectList.get(index);
                if (!isExpectedContainer(child, nextIsIndex)) {
                    child = nextIsIndex ? new ArrayList<>() : new LinkedHashMap<String, Object>();
                    objectList.set(index, child);
                }
                current = child;
            }
        }
        String leafSegment = segments.get(segments.size() - 1);
        if (current instanceof Map<?, ?>) {
            Map<String, Object> target = (Map<String, Object>) current;
            target.put(leafSegment, mergeLeafValue(target.get(leafSegment), value));
            return;
        }
        if (current instanceof List<?> && isNumeric(leafSegment)) {
            List<Object> target = (List<Object>) current;
            int index = Integer.parseInt(leafSegment);
            ensureListSize(target, index + 1);
            target.set(index, mergeLeafValue(target.get(index), value));
        }
    }

    private static boolean isExpectedContainer(Object value, boolean listExpected) {
        if (value == null) {
            return false;
        }
        return listExpected ? value instanceof List<?> : value instanceof Map<?, ?>;
    }

    private static Object mergeLeafValue(Object existing, String value) {
        if (existing == null) {
            return value;
        }
        if (existing instanceof List<?>) {
            List<Object> merged = new ArrayList<>((List<?>) existing);
            merged.add(value);
            return merged;
        }
        List<Object> merged = new ArrayList<>();
        merged.add(existing);
        merged.add(value);
        return merged;
    }

    private static void ensureListSize(List<Object> list, int expectedSize) {
        while (list.size() < expectedSize) {
            list.add(null);
        }
    }

    private static boolean isNumeric(String value) {
        return value != null && value.matches("^\\d+$");
    }

    private static String urlDecode(String value) {
        if (value == null) {
            return null;
        }
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
