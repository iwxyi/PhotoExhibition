package com.photoexhibition.service;

import java.util.Map;

public interface PaymentCallbackAdapter {

    boolean supports(PaymentProviderType providerType);

    String extractOrderNo(Map<String, Object> payload);

    String extractExternalTradeNo(Map<String, Object> payload);

    String extractOrderStatus(Map<String, Object> payload);

    default String extractReturnOrderNo(Map<String, String> queryParams) {
        return queryParams == null ? null : String.valueOf(queryParams.getOrDefault("orderNo", null));
    }
}
