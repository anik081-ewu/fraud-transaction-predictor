package com.ftd.fraud_transaction_detector.aml.feature.calculator;

import com.ftd.fraud_transaction_detector.aml.feature.domain.FeatureContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.PeerFeatures;

public class PeerFeatureCalculator {

    public PeerFeatures calculate(FeatureContext context) {
        var peer = context.peerContext();
        double amount = context.currentTransaction().amount().doubleValue();
        return new PeerFeatures(
                peer.peerGroupCode(),
                peer.averageAmount(),
                peer.medianAmount(),
                peer.standardDeviationAmount(),
                divide(amount, peer.averageAmount()),
                zScore(amount, peer.averageAmount(), peer.standardDeviationAmount()),
                peer.frequencyPercentile(),
                peer.customerType(),
                peer.customerRiskRating(),
                peer.expectedMonthlyTurnover(),
                divide(amount, peer.expectedMonthlyTurnover())
        );
    }

    private static Double divide(double numerator, Double denominator) {
        return denominator == null || denominator == 0 ? null : numerator / denominator;
    }

    private static Double zScore(double value, Double average, Double standardDeviation) {
        return average == null || standardDeviation == null || standardDeviation == 0
                ? null
                : (value - average) / standardDeviation;
    }
}
