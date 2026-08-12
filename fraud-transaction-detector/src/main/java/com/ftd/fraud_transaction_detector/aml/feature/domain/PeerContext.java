package com.ftd.fraud_transaction_detector.aml.feature.domain;

public record PeerContext(
        String peerGroupCode,
        Double averageAmount,
        Double medianAmount,
        Double standardDeviationAmount,
        Double frequencyPercentile,
        String customerType,
        String customerRiskRating,
        Double expectedMonthlyTurnover
) {
    public static PeerContext empty() {
        return new PeerContext(null, null, null, null, null, null, null, null);
    }
}
