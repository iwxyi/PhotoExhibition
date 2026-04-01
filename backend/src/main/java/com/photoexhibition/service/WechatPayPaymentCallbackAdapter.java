package com.photoexhibition.service;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(20)
public class WechatPayPaymentCallbackAdapter extends AbstractPaymentCallbackAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.WECHAT_PAY;
    }

    @Override
    public String extractOrderNo(Map<String, Object> payload) {
        return firstNonBlank(
            stringValue(payload.get("out_trade_no"), null),
            nestedString(payload, "resource", "out_trade_no"),
            nestedString(payload, "resource", "plaintext", "out_trade_no"),
            nestedString(payload, "resource", "original", "out_trade_no"),
            nestedString(payload, "resource", "attach", "orderNo"),
            stringValue(payload.get("orderNo"), null)
        );
    }

    @Override
    public String extractExternalTradeNo(Map<String, Object> payload) {
        return firstNonBlank(
            stringValue(payload.get("transaction_id"), null),
            nestedString(payload, "resource", "transaction_id"),
            nestedString(payload, "resource", "plaintext", "transaction_id"),
            nestedString(payload, "resource", "original", "transaction_id"),
            nestedString(payload, "resource", "refund_id"),
            stringValue(payload.get("id"), null)
        );
    }

    @Override
    public String extractOrderStatus(Map<String, Object> payload) {
        String eventType = firstNonBlank(
            stringValue(payload.get("event_type"), null),
            nestedString(payload, "resource", "event_type")
        );
        if ("TRANSACTION.SUCCESS".equalsIgnoreCase(eventType)) return "PAID";
        if ("REFUND.SUCCESS".equalsIgnoreCase(eventType)) return "REFUNDED";
        if ("TRANSACTION.CLOSED".equalsIgnoreCase(eventType) || "TRANSACTION.REVOKED".equalsIgnoreCase(eventType)) return "CANCELLED";
        String status = firstNonBlank(
            stringValue(payload.get("trade_state"), null),
            stringValue(payload.get("refund_status"), null),
            nestedString(payload, "resource", "trade_state"),
            nestedString(payload, "resource", "refund_status"),
            nestedString(payload, "resource", "plaintext", "trade_state"),
            nestedString(payload, "resource", "plaintext", "refund_status"),
            nestedString(payload, "resource", "original", "trade_state"),
            nestedString(payload, "resource", "original", "refund_status")
        );
        if ("SUCCESS".equalsIgnoreCase(status)) return "PAID";
        if ("CLOSED".equalsIgnoreCase(status) || "REVOKED".equalsIgnoreCase(status)) return "CANCELLED";
        if ("REFUND".equalsIgnoreCase(status) || "SUCCESS".equalsIgnoreCase(stringValue(payload.get("refund_status"), null))) return "REFUNDED";
        return "UNKNOWN";
    }

    @Override
    public String extractReturnOrderNo(Map<String, String> queryParams) {
        return firstNonBlank(
            queryParams == null ? null : queryParams.get("orderNo"),
            queryParams == null ? null : queryParams.get("out_trade_no"),
            queryParams == null ? null : queryParams.get("merchant_order_no")
        );
    }
}
