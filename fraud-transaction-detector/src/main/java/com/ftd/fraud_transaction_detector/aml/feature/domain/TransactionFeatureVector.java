package com.ftd.fraud_transaction_detector.aml.feature.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public record TransactionFeatureVector(
        String transactionId,
        String customerId,
        String accountId,
        LocalDate businessDate,
        LocalDateTime transactionDate,
        String featureVersion,
        AmountFeatures amount,
        BehaviorFeatures behavior,
        TimeFeatures time,
        VelocityFeatures velocity,
        NoveltyFeatures novelty,
        ProfileFeatures profile,
        PeerFeatures peer,
        String modelFeatureSchema,
        Map<String, Double> modelFeatures,
        Instant generatedAt
) {
    public TransactionFeatureVector {
        modelFeatures = modelFeatures == null ? Map.of() : Map.copyOf(modelFeatures);
    }
}
