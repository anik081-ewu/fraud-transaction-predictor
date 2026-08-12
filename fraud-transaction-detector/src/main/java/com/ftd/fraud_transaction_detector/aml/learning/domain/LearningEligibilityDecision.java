package com.ftd.fraud_transaction_detector.aml.learning.domain;

public record LearningEligibilityDecision(
        LearningEligibilityStatus status,
        String reason,
        boolean eligibleForIncrementalModel,
        boolean eligibleForTrustedProfile,
        boolean eligibleForBatchTraining
) {
}
