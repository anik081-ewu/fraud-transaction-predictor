package com.ftd.fraud_transaction_detector.comparison.dto;

public record ColdStartConfigItemResponse(
        String configKey,
        String configValue,
        String valueType,
        String description
) {
}
