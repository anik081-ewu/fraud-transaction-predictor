package com.ftd.fraud_transaction_detector.aml.behaviour.peer;

public record PeerBehaviourScoringPolicy(
        String normalizationVersion,
        double amountWeight,
        double frequencyWeight,
        double expectedTurnoverWeight,
        double specificGroupConfidence,
        double parentSegmentConfidence,
        double globalConfidence,
        double lowRiskThreshold,
        double mediumRiskThreshold,
        double highRiskThreshold
) {
    public PeerBehaviourScoringPolicy {
        if (normalizationVersion == null || normalizationVersion.isBlank()) {
            throw new IllegalArgumentException("normalizationVersion is required");
        }
        normalizationVersion = normalizationVersion.trim();
        double weightTotal = amountWeight + frequencyWeight + expectedTurnoverWeight;
        if (Math.abs(weightTotal - 1.0) > 0.000001) {
            throw new IllegalArgumentException("peer behaviour weights must sum to 1.0");
        }
        validateUnit(specificGroupConfidence, "specificGroupConfidence");
        validateUnit(parentSegmentConfidence, "parentSegmentConfidence");
        validateUnit(globalConfidence, "globalConfidence");
        if (!(0.0 <= lowRiskThreshold && lowRiskThreshold < mediumRiskThreshold
                && mediumRiskThreshold < highRiskThreshold && highRiskThreshold <= 1.0)) {
            throw new IllegalArgumentException("risk thresholds must be ordered within 0.0 and 1.0");
        }
    }

    public static PeerBehaviourScoringPolicy transparentV1() {
        return new PeerBehaviourScoringPolicy(
                "PEER_BEHAVIOUR_TRANSPARENT_V1",
                0.60, 0.25, 0.15,
                1.00, 0.85, 0.70,
                0.40, 0.60, 0.75
        );
    }

    private static void validateUnit(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(field + " must be between 0.0 and 1.0");
        }
    }
}
