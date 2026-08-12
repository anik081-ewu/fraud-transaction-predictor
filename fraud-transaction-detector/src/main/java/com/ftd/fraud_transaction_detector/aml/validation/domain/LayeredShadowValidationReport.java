package com.ftd.fraud_transaction_detector.aml.validation.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LayeredShadowValidationReport(
        UUID validationId,
        String riskPolicyVersion,
        String peerGroupCode,
        Instant windowStartedAt,
        Instant windowEndedAt,
        LayeredShadowValidationMetrics metrics,
        String validationStatus,
        List<String> blockingReasons,
        List<String> warnings,
        String validatedBy,
        Instant validatedAt
) {
    public LayeredShadowValidationReport {
        blockingReasons = blockingReasons == null ? List.of() : List.copyOf(blockingReasons);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
