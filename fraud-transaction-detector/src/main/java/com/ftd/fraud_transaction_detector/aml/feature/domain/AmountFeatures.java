package com.ftd.fraud_transaction_detector.aml.feature.domain;

public record AmountFeatures(
        double currentAmount,
        Double currentBalance,
        Double amountBalanceRatio,
        Double last5Average,
        Double last5Median,
        Double last30Average,
        Double last30Median,
        Double last30StandardDeviation,
        Double last30Maximum,
        Double last30Minimum,
        Double amountVsLast30Average,
        Double amountVsLast30Median,
        Double amountZScoreLast30
) {
}
