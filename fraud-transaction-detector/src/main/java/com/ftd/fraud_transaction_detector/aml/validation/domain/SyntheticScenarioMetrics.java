package com.ftd.fraud_transaction_detector.aml.validation.domain;

public record SyntheticScenarioMetrics(
        String scenarioCode,
        long expectedSuspiciousCount,
        long detectedCount,
        Double recall
) {
}
