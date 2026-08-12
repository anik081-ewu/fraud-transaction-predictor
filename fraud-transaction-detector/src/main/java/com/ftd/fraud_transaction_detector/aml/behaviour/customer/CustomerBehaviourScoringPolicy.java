package com.ftd.fraud_transaction_detector.aml.behaviour.customer;

public record CustomerBehaviourScoringPolicy(
        String normalizationVersion,
        double amountWeight,
        double frequencyWeight,
        double timeGapWeight,
        double noveltyWeight,
        double unusualHourWeight,
        double confidenceFloor,
        double lowRiskThreshold,
        double mediumRiskThreshold,
        double highRiskThreshold
) {
    public CustomerBehaviourScoringPolicy {
        if (normalizationVersion == null || normalizationVersion.isBlank()) {
            throw new IllegalArgumentException("normalizationVersion is required");
        }
        normalizationVersion = normalizationVersion.trim();
        double weightTotal = amountWeight + frequencyWeight + timeGapWeight + noveltyWeight + unusualHourWeight;
        if (Math.abs(weightTotal - 1.0) > 0.000001) {
            throw new IllegalArgumentException("customer behaviour weights must sum to 1.0");
        }
        validateUnit(confidenceFloor, "confidenceFloor");
        if (!(0.0 <= lowRiskThreshold && lowRiskThreshold < mediumRiskThreshold
                && mediumRiskThreshold < highRiskThreshold && highRiskThreshold <= 1.0)) {
            throw new IllegalArgumentException("risk thresholds must be ordered within 0.0 and 1.0");
        }
    }

    public static CustomerBehaviourScoringPolicy transparentV1() {
        return new CustomerBehaviourScoringPolicy(
                "CUSTOMER_BEHAVIOUR_TRANSPARENT_V1",
                0.55, 0.12, 0.08, 0.20, 0.05,
                0.35,
                0.40, 0.60, 0.75
        );
    }

    private static void validateUnit(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(field + " must be between 0.0 and 1.0");
        }
    }
}
