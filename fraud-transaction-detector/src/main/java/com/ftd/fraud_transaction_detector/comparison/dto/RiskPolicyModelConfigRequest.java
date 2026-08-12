package com.ftd.fraud_transaction_detector.comparison.dto;

public record RiskPolicyModelConfigRequest(
        String modelKey,
        boolean enabled,
        double weight
) {
}
