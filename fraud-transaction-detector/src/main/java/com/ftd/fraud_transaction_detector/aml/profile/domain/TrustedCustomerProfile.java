package com.ftd.fraud_transaction_detector.aml.profile.domain;

import com.ftd.fraud_transaction_detector.aml.feature.domain.ProfileStatus;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TrustedProfileSnapshot;

import java.time.LocalDateTime;

public record TrustedCustomerProfile(
        String customerId,
        TrustedProfileSnapshot snapshot,
        LocalDateTime lastLearnedAt
) {
    public boolean isPointInTimeSafe(LocalDateTime transactionDate) {
        return lastLearnedAt == null || !lastLearnedAt.isAfter(transactionDate);
    }

    public static TrustedCustomerProfile empty(String customerId) {
        return new TrustedCustomerProfile(
                customerId,
                TrustedProfileSnapshot.empty(),
                null
        );
    }

    public static TrustedProfileSnapshot snapshot(
            long count,
            Double average,
            Double variance,
            Double standardDeviation,
            Double maximum,
            Double minimum,
            Integer usualStartHour,
            Integer usualEndHour,
            String dominantChannel,
            String dominantLocation,
            double confidence,
            String status
    ) {
        return new TrustedProfileSnapshot(
                count, average, variance, standardDeviation, maximum, minimum,
                usualStartHour, usualEndHour, dominantChannel, dominantLocation,
                confidence, ProfileStatus.valueOf(status)
        );
    }
}
