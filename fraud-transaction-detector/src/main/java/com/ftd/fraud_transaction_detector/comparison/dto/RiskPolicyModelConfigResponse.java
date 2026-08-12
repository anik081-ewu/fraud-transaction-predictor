package com.ftd.fraud_transaction_detector.comparison.dto;

public record RiskPolicyModelConfigResponse(
        String modelKey,
        String displayName,
        String family,
        boolean enabled,
        double weight,
        double effectiveWeight,
        boolean productionReady
) {
}
