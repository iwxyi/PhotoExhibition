package com.photoexhibition.service;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(40)
public class PaypalPaymentCallbackAdapter extends AbstractPaymentCallbackAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.PAYPAL;
    }

    @Override
    public String extractOrderNo(Map<String, Object> payload) {
        return firstNonBlank(
            stringValue(payload.get("invoice_id"), null),
            nestedString(payload, "resource", "invoice_id"),
            nestedString(payload, "resource", "custom_id"),
            nestedString(payload, "resource", "purchase_units", "0", "invoice_id"),
            nestedString(payload, "resource", "purchase_units", "0", "custom_id"),
            stringValue(payload.get("orderNo"), null)
        );
    }

    @Override
    public String extractExternalTradeNo(Map<String, Object> payload) {
        return firstNonBlank(
            stringValue(payload.get("resource_id"), null),
            nestedString(payload, "resource", "id"),
            nestedString(payload, "resource", "supplementary_data", "related_ids", "order_id"),
            stringValue(payload.get("id"), null)
        );
    }

    @Override
    public String extractOrderStatus(Map<String, Object> payload) {
        String eventType = stringValue(payload.get("event_type"), null);
        if ("CHECKOUT.ORDER.APPROVED".equalsIgnoreCase(eventType) || "PAYMENT.CAPTURE.COMPLETED".equalsIgnoreCase(eventType)) return "PAID";
        if ("PAYMENT.CAPTURE.REFUNDED".equalsIgnoreCase(eventType)) return "REFUNDED";
        if ("CHECKOUT.ORDER.VOIDED".equalsIgnoreCase(eventType)) return "CANCELLED";
        String resourceStatus = firstNonBlank(
            nestedString(payload, "resource", "status"),
            nestedString(payload, "resource", "purchase_units", "0", "payments", "captures", "0", "status")
        );
        if ("COMPLETED".equalsIgnoreCase(resourceStatus)) return "PAID";
        if ("REFUNDED".equalsIgnoreCase(resourceStatus)) return "REFUNDED";
        if ("VOIDED".equalsIgnoreCase(resourceStatus) || "CANCELLED".equalsIgnoreCase(resourceStatus)) return "CANCELLED";
        return "UNKNOWN";
    }

    @Override
    public String extractReturnOrderNo(Map<String, String> queryParams) {
        return firstNonBlank(
            queryParams == null ? null : queryParams.get("orderNo"),
            queryParams == null ? null : queryParams.get("invoice_id"),
            queryParams == null ? null : queryParams.get("custom_id")
        );
    }
}
