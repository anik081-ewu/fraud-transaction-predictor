package com.ftd.fraud_transaction_detector.aml.feature.domain;

public record BehaviorFeatures(
        Double last30DebitRatio,
        Double last30CreditRatio,
        Double last30CashRatio,
        int last30UniqueBeneficiaries,
        int last30UniqueLocations,
        int last30UniqueChannels,
        Double last30AverageTimeGapMinutes
) {
}
