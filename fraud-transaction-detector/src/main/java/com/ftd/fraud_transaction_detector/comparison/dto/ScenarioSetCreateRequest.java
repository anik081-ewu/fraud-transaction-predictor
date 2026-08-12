package com.ftd.fraud_transaction_detector.comparison.dto;

public record ScenarioSetCreateRequest(
        String name,
        String description,
        String createdBy
) {
}
