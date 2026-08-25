package com.photoexhibition.service;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class DefaultPaymentCallbackAdapter extends AbstractPaymentCallbackAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return true;
    }

    @Override
    public String extractOrderNo(Map<String, Object> payload) {
        return stringValue(payload.get("orderNo"), null);
    }

    @Override
    public String extractExternalTradeNo(Map<String, Object> payload) {
        return stringValue(payload.get("tradeNo"), null);
    }

    @Override
    public String extractOrderStatus(Map<String, Object> payload) {
        String status = stringValue(payload.get("status"), null);
        if ("PAID".equalsIgnoreCase(status)) return "PAID";
        if ("REFUNDED".equalsIgnoreCase(status)) return "REFUNDED";
        if ("CANCELLED".equalsIgnoreCase(status) || "CANCELED".equalsIgnoreCase(status)) return "CANCELLED";
        return "UNKNOWN";
    }
}
