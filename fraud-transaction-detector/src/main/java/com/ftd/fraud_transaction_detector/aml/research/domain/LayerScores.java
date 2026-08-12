package com.ftd.fraud_transaction_detector.aml.research.domain;

public record LayerScores(
        double customerBehaviour,
        double peerBehaviour,
        double mlEnsemble,
        double rules,
        boolean hardRuleOverride
) {
}
