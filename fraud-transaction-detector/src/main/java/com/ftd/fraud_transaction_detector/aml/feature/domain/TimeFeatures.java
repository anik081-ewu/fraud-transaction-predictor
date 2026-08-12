package com.ftd.fraud_transaction_detector.aml.feature.domain;

public record TimeFeatures(
        int transactionHour,
        int transactionDayOfWeek,
        boolean night,
        boolean weekend,
        Double minutesSincePreviousTransaction
) {
}
