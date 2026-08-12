package com.ftd.fraud_transaction_detector.aml.feature.domain;

public record TrustedProfileSnapshot(
        long transactionCount,
        Double averageAmount,
        Double varianceAmount,
        Double standardDeviationAmount,
        Double maximumAmount,
        Double minimumAmount,
        Integer usualStartHour,
        Integer usualEndHour,
        String dominantChannel,
        String dominantLocation,
        double confidence,
        ProfileStatus status
) {
    public TrustedProfileSnapshot {
        if (transactionCount < 0) {
            throw new IllegalArgumentException("transactionCount cannot be negative");
        }
        if (confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        status = status == null ? ProfileStatus.COLD_START : status;
    }

    public static TrustedProfileSnapshot empty() {
        return new TrustedProfileSnapshot(
                0, null, null, null, null, null,
                null, null, null, null, 0, ProfileStatus.COLD_START
        );
    }
}
