package com.photoexhibition.service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class AbstractPaymentCallbackAdapter implements PaymentCallbackAdapter {

    private static final Pattern JSON_ORDER_NO_PATTERN =
        Pattern.compile("\"(?:orderNo|order_no|out_trade_no|merchant_order_no|invoice_id|reference_id|client_reference_id|merchantReference)\"\\s*:\\s*\"([^\"]+)\"");

    protected String stringValue(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) {
                String normalizedItem = stringValue(item, null);
                if (normalizedItem != null) {
                    return normalizedItem;
                }
            }
            return defaultValue;
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
                String normalizedItem = stringValue(java.lang.reflect.Array.get(value, index), null);
                if (normalizedItem != null) {
                    return normalizedItem;
                }
            }
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }

    @SuppressWarnings("unchecked")
    protected String nestedString(Map<String, Object> payload, String... keys) {
        Object current = payload;
        for (String key : keys) {
            if (current == null) {
                return null;
            }
            if (current instanceof Map) {
                Map<String, Object> currentMap = (Map<String, Object>) current;
                if (currentMap.containsKey(key)) {
                    current = currentMap.get(key);
                    continue;
                }
                String flattenedDotPath = flattenPath(keys);
                if (flattenedDotPath != null && currentMap.containsKey(flattenedDotPath)) {
                    return stringValue(currentMap.get(flattenedDotPath), null);
                }
                String flattenedBracketPath = flattenBracketPath(keys);
                if (flattenedBracketPath != null && currentMap.containsKey(flattenedBracketPath)) {
                    return stringValue(currentMap.get(flattenedBracketPath), null);
                }
                current = null;
            } else if (current instanceof Iterable && key.matches("^\\d+$")) {
                int index = Integer.parseInt(key);
                int currentIndex = 0;
                Object matched = null;
                for (Object item : (Iterable<?>) current) {
                    if (currentIndex == index) {
                        matched = item;
                        break;
                    }
                    currentIndex++;
                }
                current = matched;
            } else {
                return null;
            }
        }
        return stringValue(current, null);
    }

    private String flattenPath(String... keys) {
        if (keys == null || keys.length == 0) {
            return null;
        }
        return String.join(".", keys);
    }

    private String flattenBracketPath(String... keys) {
        if (keys == null || keys.length == 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < keys.length; index++) {
            String key = keys[index];
            if (key == null || key.isBlank()) {
                return null;
            }
            if (index == 0) {
                builder.append(key);
            } else {
                builder.append('[').append(key).append(']');
            }
        }
        return builder.toString();
    }

    protected String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    protected Map<String, Object> copyPayload(Map<String, Object> payload) {
        return payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
    }

    protected String embeddedOrderNo(Object value) {
        String text = stringValue(value, null);
        if (text == null) {
            return null;
        }
        String direct = extractKnownOrderNo(text);
        if (direct != null) {
            return direct;
        }
        String decoded = safeDecode(text);
        if (decoded != null && !decoded.equals(text)) {
            return extractKnownOrderNo(decoded);
        }
        return null;
    }

    private String extractKnownOrderNo(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        if (text.contains("=")) {
            for (String candidateKey : new String[] {
                "orderNo", "order_no", "out_trade_no", "merchant_order_no",
                "invoice_id", "reference_id", "client_reference_id", "merchantReference"
            }) {
                String queryValue = queryLikeValue(text, candidateKey);
                if (queryValue != null) {
                    return queryValue;
                }
            }
        }
        Matcher matcher = JSON_ORDER_NO_PATTERN.matcher(text);
        if (matcher.find()) {
            return stringValue(matcher.group(1), null);
        }
        return null;
    }

    private String queryLikeValue(String raw, String key) {
        if (raw == null || raw.isBlank() || key == null || key.isBlank() || !raw.contains("=")) {
            return null;
        }
        for (String pair : raw.split("&")) {
            if (pair == null || pair.isBlank()) {
                continue;
            }
            int equalsIndex = pair.indexOf('=');
            String rawKey = equalsIndex >= 0 ? pair.substring(0, equalsIndex) : pair;
            String rawValue = equalsIndex >= 0 ? pair.substring(equalsIndex + 1) : "";
            if (key.equals(safeDecode(rawKey))) {
                return stringValue(safeDecode(rawValue), null);
            }
        }
        return null;
    }

    private String safeDecode(String value) {
        if (value == null) {
            return null;
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }
}
