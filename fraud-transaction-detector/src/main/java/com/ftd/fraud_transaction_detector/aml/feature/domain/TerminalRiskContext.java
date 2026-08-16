package com.ftd.fraud_transaction_detector.aml.feature.domain;

public record TerminalRiskContext(
        boolean enabled,
        TerminalWindowStatistics oneDay,
        TerminalWindowStatistics sevenDays,
        TerminalWindowStatistics thirtyDays,
        double globalConfirmedFraudRate,
        double smoothingStrength,
        int minimumTransactions
) {
    public TerminalRiskContext {
        oneDay = oneDay == null ? TerminalWindowStatistics.empty() : oneDay;
        sevenDays = sevenDays == null ? TerminalWindowStatistics.empty() : sevenDays;
        thirtyDays = thirtyDays == null ? TerminalWindowStatistics.empty() : thirtyDays;
        if (!Double.isFinite(globalConfirmedFraudRate) || globalConfirmedFraudRate < 0.0 || globalConfirmedFraudRate > 1.0) {
            throw new IllegalArgumentException("Global confirmed fraud rate must be between 0 and 1");
        }
        if (!Double.isFinite(smoothingStrength) || smoothingStrength < 0.0) {
            throw new IllegalArgumentException("Smoothing strength cannot be negative");
        }
        if (minimumTransactions < 1) {
            throw new IllegalArgumentException("Minimum transactions must be positive");
        }
    }

    public static TerminalRiskContext disabled() {
        return new TerminalRiskContext(
                false,
                TerminalWindowStatistics.empty(),
                TerminalWindowStatistics.empty(),
                TerminalWindowStatistics.empty(),
                0.0,
                20.0,
                3
        );
    }
}
