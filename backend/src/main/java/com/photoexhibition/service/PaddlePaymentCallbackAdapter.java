package com.photoexhibition.service;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(60)
public class PaddlePaymentCallbackAdapter extends AbstractPaymentCallbackAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.PADDLE;
    }

    @Override
    public String extractOrderNo(Map<String, Object> payload) {
        return firstNonBlank(
            stringValue(payload.get("orderNo"), null),
            nestedString(payload, "data", "custom_data", "orderNo"),
            nestedString(payload, "custom_data", "orderNo")
        );
    }

    @Override
    public String extractExternalTradeNo(Map<String, Object> payload) {
        return firstNonBlank(
            stringValue(payload.get("notification_id"), null),
            nestedString(payload, "data", "id"),
            stringValue(payload.get("id"), null)
        );
    }

    @Override
    public String extractOrderStatus(Map<String, Object> payload) {
        String eventType = stringValue(payload.get("event_type"), null);
        if ("transaction.paid".equalsIgnoreCase(eventType)) return "PAID";
        if ("transaction.canceled".equalsIgnoreCase(eventType)) return "CANCELLED";
        if ("transaction.refunded".equalsIgnoreCase(eventType)) return "REFUNDED";
        return "UNKNOWN";
    }
}
