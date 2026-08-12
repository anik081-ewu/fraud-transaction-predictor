package com.ftd.fraud_transaction_detector.aml.research.api;

public record LayerAblationResult(
        int partitionPercentage,
        long evaluatedRows,
        String variant,
        double averageRiskScore,
        double suspiciousRate,
        long suspiciousCount,
        long decisionChangesVsFull,
        double decisionChangeRateVsFull,
        double averageScoreDeltaVsFull,
        long hardRuleOverrides
) {
}
