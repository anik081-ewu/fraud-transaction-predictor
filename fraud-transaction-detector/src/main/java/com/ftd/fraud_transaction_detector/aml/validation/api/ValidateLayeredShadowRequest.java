package com.ftd.fraud_transaction_detector.aml.validation.api;

import java.time.Instant;

public record ValidateLayeredShadowRequest(
        String riskPolicyVersion,
        String peerGroupCode,
        Instant windowStartedAt,
        Instant windowEndedAt,
        String validatedBy
) {
}
