package com.ftd.fraud_transaction_detector.aml.feature.domain;

public record TerminalWindowStatistics(
        long transactionCount,
        double averageAmount,
        long confirmedLabelCount,
        long confirmedFraudCount
) {
    public TerminalWindowStatistics {
        if (transactionCount < 0 || confirmedLabelCount < 0 || confirmedFraudCount < 0) {
            throw new IllegalArgumentException("Terminal statistics cannot contain negative counts");
        }
        if (confirmedFraudCount > confirmedLabelCount) {
            throw new IllegalArgumentException("Confirmed fraud count cannot exceed confirmed label count");
        }
    }

    public static TerminalWindowStatistics empty() {
        return new TerminalWindowStatistics(0, 0.0, 0, 0);
    }
}
