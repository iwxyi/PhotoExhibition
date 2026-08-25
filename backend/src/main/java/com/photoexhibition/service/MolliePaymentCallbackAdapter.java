package com.photoexhibition.service;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(75)
public class MolliePaymentCallbackAdapter extends AbstractPaymentCallbackAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.MOLLIE;
    }

    @Override
    public String extractOrderNo(Map<String, Object> payload) {
        return firstNonBlank(
            nestedString(payload, "metadata", "orderNo"),
            stringValue(payload.get("orderNo"), null)
        );
    }

    @Override
    public String extractExternalTradeNo(Map<String, Object> payload) {
        return firstNonBlank(
            stringValue(payload.get("id"), null),
            nestedString(payload, "payment", "id")
        );
    }

    @Override
    public String extractOrderStatus(Map<String, Object> payload) {
        String status = stringValue(payload.get("status"), null);
        if ("paid".equalsIgnoreCase(status)) return "PAID";
        if ("refunded".equalsIgnoreCase(status)) return "REFUNDED";
        if ("canceled".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status) || "expired".equalsIgnoreCase(status)) return "CANCELLED";
        return "UNKNOWN";
    }

    @Override
    public String extractReturnOrderNo(Map<String, String> queryParams) {
        return queryParams == null ? null : queryParams.get("orderNo");
    }
}
