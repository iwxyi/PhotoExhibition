package com.photoexhibition.service;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(65)
public class LemonSqueezyPaymentCallbackAdapter extends AbstractPaymentCallbackAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.LEMON_SQUEEZY;
    }

    @Override
    public String extractOrderNo(Map<String, Object> payload) {
        return firstNonBlank(
            stringValue(payload.get("orderNo"), null),
            nestedString(payload, "meta", "custom_data", "orderNo")
        );
    }

    @Override
    public String extractExternalTradeNo(Map<String, Object> payload) {
        return firstNonBlank(
            nestedString(payload, "data", "id"),
            stringValue(payload.get("id"), null)
        );
    }

    @Override
    public String extractOrderStatus(Map<String, Object> payload) {
        String status = nestedString(payload, "data", "attributes", "status");
        if ("paid".equalsIgnoreCase(status)) return "PAID";
        if ("refunded".equalsIgnoreCase(status)) return "REFUNDED";
        if ("cancelled".equalsIgnoreCase(status) || "canceled".equalsIgnoreCase(status)) return "CANCELLED";
        return "UNKNOWN";
    }

    @Override
    public String extractReturnOrderNo(Map<String, String> queryParams) {
        return queryParams == null ? null : queryParams.get("orderNo");
    }
}
