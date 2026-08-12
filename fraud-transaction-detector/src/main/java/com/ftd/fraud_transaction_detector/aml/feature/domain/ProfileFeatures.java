package com.ftd.fraud_transaction_detector.aml.feature.domain;

public record ProfileFeatures(
        long customerHistoryCount,
        long trustedHistoryCount,
        int recentTransactionCount,
        double confidence,
        ProfileStatus status
) {
}
