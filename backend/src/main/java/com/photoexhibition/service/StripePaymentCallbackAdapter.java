package com.photoexhibition.service;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(30)
public class StripePaymentCallbackAdapter extends AbstractPaymentCallbackAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.STRIPE;
    }

    @Override
    public String extractOrderNo(Map<String, Object> payload) {
        return firstNonBlank(
            stringValue(payload.get("client_reference_id"), null),
            nestedString(payload, "data", "object", "client_reference_id"),
            nestedString(payload, "data", "object", "metadata", "orderNo"),
            nestedString(payload, "data", "object", "metadata", "order_no"),
            nestedString(payload, "data", "object", "payment_link_metadata", "orderNo"),
            nestedString(payload, "data", "object", "subscription_details", "metadata", "orderNo"),
            stringValue(payload.get("orderNo"), null)
        );
    }

    @Override
    public String extractExternalTradeNo(Map<String, Object> payload) {
        return firstNonBlank(
            stringValue(payload.get("event_id"), null),
            stringValue(payload.get("id"), null),
            nestedString(payload, "data", "object", "payment_intent"),
            nestedString(payload, "data", "object", "id"),
            nestedString(payload, "data", "object", "charge")
        );
    }

    @Override
    public String extractOrderStatus(Map<String, Object> payload) {
        String type = stringValue(payload.get("type"), null);
        if ("checkout.session.completed".equalsIgnoreCase(type) || "payment_intent.succeeded".equalsIgnoreCase(type)) return "PAID";
        if ("charge.refunded".equalsIgnoreCase(type) || "refund.updated".equalsIgnoreCase(type)) return "REFUNDED";
        if ("payment_intent.canceled".equalsIgnoreCase(type) || "checkout.session.expired".equalsIgnoreCase(type)
            || "payment_intent.payment_failed".equalsIgnoreCase(type)) return "CANCELLED";
        return "UNKNOWN";
    }

    @Override
    public String extractReturnOrderNo(Map<String, String> queryParams) {
        return firstNonBlank(
            queryParams == null ? null : queryParams.get("orderNo"),
            queryParams == null ? null : queryParams.get("client_reference_id"),
            queryParams == null ? null : queryParams.get("order_no")
        );
    }
}
