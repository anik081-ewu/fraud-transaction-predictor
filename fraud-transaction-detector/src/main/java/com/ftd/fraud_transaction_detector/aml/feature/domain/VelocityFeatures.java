package com.ftd.fraud_transaction_detector.aml.feature.domain;

public record VelocityFeatures(
        int transactionCount10Minutes,
        int transactionCount1Hour,
        int transactionCount24Hours,
        int transactionCount7Days,
        int transactionCount30Days,
        double amountSum10Minutes,
        double amountSum1Hour,
        double amountSum24Hours,
        double amountSum7Days,
        double amountSum30Days,
        int uniqueBeneficiaries1Hour,
        int uniqueBeneficiaries24Hours,
        int uniqueBeneficiaries7Days,
        int repeatedAmountCount24Hours,
        int belowThresholdCount24Hours,
        double belowThresholdAmountSum24Hours
) {
}
