package com.ftd.fraud_transaction_detector.aml.risk.domain;

public record ComponentScores(
        double customerBehaviour,
        double peerBehaviour,
        double mlEnsemble,
        double rules
) {
    public ComponentScores {
        validate(customerBehaviour, "customerBehaviour");
        validate(peerBehaviour, "peerBehaviour");
        validate(mlEnsemble, "mlEnsemble");
        validate(rules, "rules");
    }

    private static void validate(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(field + " must be between 0.0 and 1.0");
        }
    }
}
