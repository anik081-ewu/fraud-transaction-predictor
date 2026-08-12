package com.ftd.fraud_transaction_detector.aml.validation.api;

import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ValidateCandidateRequest(
        Instant windowStartedAt,
        Instant windowEndedAt,
        @Size(max = 100) String validatedBy
) {
}
