package com.ftd.fraud_transaction_detector.aml.feature.domain;

public record PeerFeatures(
        String peerGroupCode,
        Double peerAverageAmount,
        Double peerMedianAmount,
        Double peerStandardDeviationAmount,
        Double amountVsPeerAverage,
        Double peerAmountZScore,
        Double peerFrequencyPercentile,
        String customerType,
        String customerRiskRating,
        Double expectedMonthlyTurnover,
        Double amountVsExpectedTurnover
) {
}
