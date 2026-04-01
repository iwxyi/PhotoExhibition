package com.photoexhibition.service;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(10)
public class AlipayPaymentCallbackAdapter extends AbstractPaymentCallbackAdapter {

    @Override
    public boolean supports(PaymentProviderType providerType) {
        return providerType == PaymentProviderType.ALIPAY;
    }

    @Override
    public String extractOrderNo(Map<String, Object> payload) {
        return firstNonBlank(
            stringValue(payload.get("out_trade_no"), null),
            stringValue(payload.get("orderNo"), null),
            nestedString(payload, "biz_content", "out_trade_no"),
            nestedString(payload, "notify_data", "out_trade_no"),
            nestedString(payload, "metadata", "orderNo")
        );
    }

    @Override
    public String extractExternalTradeNo(Map<String, Object> payload) {
        return firstNonBlank(
            stringValue(payload.get("trade_no"), null),
            stringValue(payload.get("id"), null),
            nestedString(payload, "biz_content", "trade_no"),
            nestedString(payload, "notify_data", "trade_no")
        );
    }

    @Override
    public String extractOrderStatus(Map<String, Object> payload) {
        String status = firstNonBlank(
            stringValue(payload.get("trade_status"), null),
            stringValue(payload.get("refund_status"), null),
            nestedString(payload, "biz_content", "trade_status"),
            nestedString(payload, "notify_data", "trade_status")
        );
        if ("TRADE_SUCCESS".equalsIgnoreCase(status)) return "PAID";
        if ("TRADE_CLOSED".equalsIgnoreCase(status)) return "CANCELLED";
        if ("REFUND_SUCCESS".equalsIgnoreCase(status)) return "REFUNDED";
        if (payload != null && firstNonBlank(
            stringValue(payload.get("gmt_refund_pay"), null),
            nestedString(payload, "biz_content", "gmt_refund_pay"),
            nestedString(payload, "notify_data", "gmt_refund_pay")
        ) != null) {
            return "REFUNDED";
        }
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
