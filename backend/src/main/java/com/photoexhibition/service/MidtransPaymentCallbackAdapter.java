package com.photoexhibition.service;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(90)
public class MidtransPaymentCallbackAdapter extends AbstractPaymentCallbackAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.MIDTRANS;
    }

    @Override
    public String extractOrderNo(Map<String, Object> payload) {
        return firstNonBlank(
            stringValue(payload.get("order_id"), null),
            stringValue(payload.get("orderNo"), null)
        );
    }

    @Override
    public String extractExternalTradeNo(Map<String, Object> payload) {
        return firstNonBlank(
            stringValue(payload.get("transaction_id"), null),
            stringValue(payload.get("id"), null)
        );
    }

    @Override
    public String extractOrderStatus(Map<String, Object> payload) {
        String status = stringValue(payload.get("transaction_status"), null);
        if ("settlement".equalsIgnoreCase(status) || "capture".equalsIgnoreCase(status)) return "PAID";
        if ("refund".equalsIgnoreCase(status) || "partial_refund".equalsIgnoreCase(status)) return "REFUNDED";
        if ("cancel".equalsIgnoreCase(status) || "deny".equalsIgnoreCase(status) || "expire".equalsIgnoreCase(status)) return "CANCELLED";
        return "UNKNOWN";
    }

    @Override
    public String extractReturnOrderNo(Map<String, String> queryParams) {
        return firstNonBlank(
            queryParams == null ? null : queryParams.get("orderNo"),
            queryParams == null ? null : queryParams.get("order_id")
        );
    }
}
