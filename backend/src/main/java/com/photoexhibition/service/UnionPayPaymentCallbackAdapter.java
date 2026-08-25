package com.photoexhibition.service;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(50)
public class UnionPayPaymentCallbackAdapter extends AbstractPaymentCallbackAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.UNIONPAY;
    }

    @Override
    public String extractOrderNo(Map<String, Object> payload) {
        return firstNonBlank(
            stringValue(payload.get("orderNo"), null),
            stringValue(payload.get("orderId"), null),
            stringValue(payload.get("merOrderId"), null)
        );
    }

    @Override
    public String extractExternalTradeNo(Map<String, Object> payload) {
        return firstNonBlank(
            stringValue(payload.get("queryId"), null),
            stringValue(payload.get("traceNo"), null),
            stringValue(payload.get("tn"), null)
        );
    }

    @Override
    public String extractOrderStatus(Map<String, Object> payload) {
        if ("00".equalsIgnoreCase(stringValue(payload.get("respCode"), null))) return "PAID";
        if ("REFUNDED".equalsIgnoreCase(stringValue(payload.get("status"), null))) return "REFUNDED";
        if ("CANCELLED".equalsIgnoreCase(stringValue(payload.get("status"), null))) return "CANCELLED";
        return "UNKNOWN";
    }

    @Override
    public String extractReturnOrderNo(Map<String, String> queryParams) {
        return firstNonBlank(
            queryParams == null ? null : queryParams.get("orderNo"),
            queryParams == null ? null : queryParams.get("orderId"),
            queryParams == null ? null : queryParams.get("merOrderId")
        );
    }
}
