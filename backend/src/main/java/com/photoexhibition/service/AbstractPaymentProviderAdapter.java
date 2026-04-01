package com.photoexhibition.service;

import com.photoexhibition.entity.UserPlanOrder;
import com.photoexhibition.entity.VipPlan;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.ZoneId;
import java.util.Base64;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

abstract class AbstractPaymentProviderAdapter implements PaymentProviderAdapter {

    protected Map<String, Object> baseLaunchPayload(UserPlanOrder order,
                                                    VipPlan plan,
                                                    PaymentProviderType providerType,
                                                    PaymentGatewayService.PaymentPreview preview) {
        Map<String, Object> launchPayload = new LinkedHashMap<>();
        launchPayload.put("orderNo", order.getOrderNo());
        launchPayload.put("providerType", providerType.name());
        launchPayload.put("subject", plan.getName());
        launchPayload.put("amountFen", order.getAmountFen());
        launchPayload.put("currency", preview.getCurrency());
        launchPayload.put("requestPayload", preview.getRequestPayload());
        return launchPayload;
    }

    protected String maskKey(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "<未配置>";
        }
        if (rawValue.length() <= 8) {
            return "****";
        }
        return rawValue.substring(0, 4) + "****" + rawValue.substring(rawValue.length() - 4);
    }

    protected boolean canSignWithPrivateKey(String privateKey) {
        if (privateKey == null || privateKey.isBlank()) {
            return false;
        }
        String normalized = privateKey.trim();
        if (normalized.contains("BEGIN PRIVATE KEY") || normalized.contains("BEGIN RSA PRIVATE KEY")) {
            return true;
        }
        return normalized.matches("^[A-Za-z0-9+/=\\r\\n]+$") && normalized.replaceAll("\\s+", "").length() > 128;
    }

    protected String signSha256WithRsaBase64(String privateKey, String content) {
        try {
            PrivateKey key = parsePrivateKey(privateKey);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(key);
            signature.update(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new RuntimeException("支付签名生成失败", e);
        }
    }

    protected String resolveEpochSeconds(UserPlanOrder order) {
        if (order != null && order.getCreatedAt() != null) {
            return String.valueOf(order.getCreatedAt().atZone(ZoneId.systemDefault()).toEpochSecond());
        }
        return String.valueOf(System.currentTimeMillis() / 1000);
    }

    protected String toJson(Object value) {
        if (value instanceof Map) {
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append('"').append(escapeJson(String.valueOf(entry.getKey()))).append('"').append(':')
                    .append(toJson(entry.getValue()));
            }
            return builder.append('}').toString();
        }
        if (value instanceof List) {
            StringBuilder builder = new StringBuilder("[");
            boolean first = true;
            for (Object item : (List<?>) value) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(toJson(item));
            }
            return builder.append(']').toString();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value == null) {
            return "null";
        }
        return "\"" + escapeJson(String.valueOf(value)) + "\"";
    }

    protected String toFormUrlEncoded(Object value) {
        List<String> pairs = new ArrayList<>();
        appendFormPairs(pairs, null, value);
        return String.join("&", pairs);
    }

    @SuppressWarnings("unchecked")
    private void appendFormPairs(List<String> pairs, String prefix, Object value) {
        if (value instanceof Map) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                String nextPrefix = prefix == null ? entry.getKey() : prefix + "[" + entry.getKey() + "]";
                appendFormPairs(pairs, nextPrefix, entry.getValue());
            }
            return;
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            for (int i = 0; i < list.size(); i++) {
                String nextPrefix = prefix + "[" + i + "]";
                appendFormPairs(pairs, nextPrefix, list.get(i));
            }
            return;
        }
        if (prefix == null || prefix.isBlank() || value == null) {
            return;
        }
        pairs.add(urlEncode(prefix) + "=" + urlEncode(String.valueOf(value)));
    }

    protected String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String escapeJson(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }

    private PrivateKey parsePrivateKey(String rawKey) throws Exception {
        String normalized = rawKey == null ? "" : rawKey.trim();
        normalized = normalized
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }
}
