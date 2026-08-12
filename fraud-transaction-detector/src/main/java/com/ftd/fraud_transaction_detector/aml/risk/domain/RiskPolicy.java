package com.ftd.fraud_transaction_detector.aml.risk.domain;

public record RiskPolicy(
        String version,
        double customerBehaviourWeight,
        double peerBehaviourWeight,
        double mlEnsembleWeight,
        double rulesWeight,
        double lowRiskThreshold,
        double mediumRiskThreshold,
        double highRiskThreshold
) {
    public RiskPolicy {
        if (version == null || version.isBlank()) throw new IllegalArgumentException("version is required");
        version = version.trim();
        validateWeight(customerBehaviourWeight, "customerBehaviourWeight");
        validateWeight(peerBehaviourWeight, "peerBehaviourWeight");
        validateWeight(mlEnsembleWeight, "mlEnsembleWeight");
        validateWeight(rulesWeight, "rulesWeight");
        double total = customerBehaviourWeight + peerBehaviourWeight + mlEnsembleWeight + rulesWeight;
        if (Math.abs(total - 1.0) > 0.000001) {
            throw new IllegalArgumentException("risk-policy weights must sum to 1.0");
        }
        if (!(0.0 <= lowRiskThreshold && lowRiskThreshold < mediumRiskThreshold
                && mediumRiskThreshold < highRiskThreshold && highRiskThreshold <= 1.0)) {
            throw new IllegalArgumentException("risk thresholds must be ordered within 0.0 and 1.0");
        }
    }

    private static void validateWeight(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(field + " must be between 0.0 and 1.0");
        }
    }
}
