package com.ftd.fraud_transaction_detector.aml.scoring.domain;

public record NormalizedScore(
        double rawScore,
        double normalizedScore,
        String normalizationVersion
) {
    public NormalizedScore {
        if (!Double.isFinite(rawScore)) throw new IllegalArgumentException("rawScore must be finite");
        if (!Double.isFinite(normalizedScore) || normalizedScore < 0.0 || normalizedScore > 1.0) {
            throw new IllegalArgumentException("normalizedScore must be between 0.0 and 1.0");
        }
        if (normalizationVersion == null || normalizationVersion.isBlank()) {
            throw new IllegalArgumentException("normalizationVersion is required");
        }
        normalizationVersion = normalizationVersion.trim();
    }
}
