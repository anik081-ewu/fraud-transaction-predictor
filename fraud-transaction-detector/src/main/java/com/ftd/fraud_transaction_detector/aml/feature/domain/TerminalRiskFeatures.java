package com.ftd.fraud_transaction_detector.aml.feature.domain;

public record TerminalRiskFeatures(
        long transactionCount1Day,
        long confirmedFraudCount1Day,
        double fraudRate1Day,
        double averageAmount1Day,
        long transactionCount7Days,
        long confirmedFraudCount7Days,
        double fraudRate7Days,
        double averageAmount7Days,
        long transactionCount30Days,
        long confirmedFraudCount30Days,
        double fraudRate30Days,
        double averageAmount30Days,
        boolean available
) {
    public static TerminalRiskFeatures empty() {
        return new TerminalRiskFeatures(0, 0, 0.0, 0.0, 0, 0, 0.0, 0.0, 0, 0, 0.0, 0.0, false);
    }
}
