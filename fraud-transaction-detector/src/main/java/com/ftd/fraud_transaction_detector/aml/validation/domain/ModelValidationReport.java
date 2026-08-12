package com.ftd.fraud_transaction_detector.aml.validation.domain;

import java.time.Instant;
import java.util.UUID;

public record ModelValidationReport(
        UUID validationId,
        String modelVersion,
        String comparisonTarget,
        Instant windowStartedAt,
        Instant windowEndedAt,
        ChallengerMetrics metrics,
        String validationStatus,
        String failureReason,
        String validatedBy,
        Instant validatedAt
) {
}
