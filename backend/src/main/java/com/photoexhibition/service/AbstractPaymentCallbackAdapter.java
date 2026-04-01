package com.photoexhibition.service;

import java.util.LinkedHashMap;
import java.util.Map;

abstract class AbstractPaymentCallbackAdapter implements PaymentCallbackAdapter {

    protected String stringValue(Object value, String defaultValue) {
        if (value == null) {
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
                current = ((Map<String, Object>) current).get(key);
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
}
