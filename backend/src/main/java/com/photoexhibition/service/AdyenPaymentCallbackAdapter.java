package com.photoexhibition.service;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(70)
public class AdyenPaymentCallbackAdapter extends AbstractPaymentCallbackAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.ADYEN;
    }

    @Override
    public String extractOrderNo(Map<String, Object> payload) {
        return firstNonBlank(
            stringValue(payload.get("merchantReference"), null),
            nestedString(payload, "notificationItems", "0", "NotificationRequestItem", "merchantReference"),
            stringValue(payload.get("orderNo"), null)
        );
    }

    @Override
    public String extractExternalTradeNo(Map<String, Object> payload) {
        return firstNonBlank(
            stringValue(payload.get("pspReference"), null),
            nestedString(payload, "notificationItems", "0", "NotificationRequestItem", "pspReference"),
            stringValue(payload.get("id"), null)
        );
    }

    @Override
    public String extractOrderStatus(Map<String, Object> payload) {
        String eventCode = firstNonBlank(
            stringValue(payload.get("eventCode"), null),
            nestedString(payload, "notificationItems", "0", "NotificationRequestItem", "eventCode")
        );
        String success = firstNonBlank(
            stringValue(payload.get("success"), null),
            nestedString(payload, "notificationItems", "0", "NotificationRequestItem", "success")
        );
        if ("AUTHORISATION".equalsIgnoreCase(eventCode) && "true".equalsIgnoreCase(success)) return "PAID";
        if ("REFUND".equalsIgnoreCase(eventCode) && "true".equalsIgnoreCase(success)) return "REFUNDED";
        if ("CANCELLATION".equalsIgnoreCase(eventCode) && "true".equalsIgnoreCase(success)) return "CANCELLED";
        return "UNKNOWN";
    }

    @Override
    public String extractReturnOrderNo(Map<String, String> queryParams) {
        return firstNonBlank(
            queryParams == null ? null : queryParams.get("orderNo"),
            queryParams == null ? null : queryParams.get("merchantReference")
        );
    }
}
