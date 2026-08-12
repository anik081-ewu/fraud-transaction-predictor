package com.ftd.fraud_transaction_detector.aml.peer.domain;

public record PeerGroupStats(
        double averageAmount,
        double medianAmount,
        double standardDeviationAmount,
        double expectedMonthlyTurnover,
        long sampleCount
) {}
