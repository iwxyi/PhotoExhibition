package com.photoexhibition.service;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(80)
public class XenditPaymentCallbackAdapter extends AbstractPaymentCallbackAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.XENDIT;
    }

    @Override
    public String extractOrderNo(Map<String, Object> payload) {
        return firstNonBlank(
            stringValue(payload.get("reference_id"), null),
            nestedString(payload, "data", "reference_id"),
            stringValue(payload.get("orderNo"), null)
        );
    }

    @Override
    public String extractExternalTradeNo(Map<String, Object> payload) {
        return firstNonBlank(
            stringValue(payload.get("id"), null),
            nestedString(payload, "data", "id")
        );
    }

    @Override
    public String extractOrderStatus(Map<String, Object> payload) {
        String event = firstNonBlank(stringValue(payload.get("event"), null), stringValue(payload.get("status"), null));
        if ("payment.succeeded".equalsIgnoreCase(event) || "SUCCEEDED".equalsIgnoreCase(event) || "PAID".equalsIgnoreCase(event)) return "PAID";
        if ("payment.refunded".equalsIgnoreCase(event) || "REFUNDED".equalsIgnoreCase(event)) return "REFUNDED";
        if ("payment.cancelled".equalsIgnoreCase(event) || "CANCELLED".equalsIgnoreCase(event) || "FAILED".equalsIgnoreCase(event)) return "CANCELLED";
        return "UNKNOWN";
    }

    @Override
    public String extractReturnOrderNo(Map<String, String> queryParams) {
        return firstNonBlank(
            queryParams == null ? null : queryParams.get("orderNo"),
            queryParams == null ? null : queryParams.get("reference_id")
        );
    }
}
