package com.ftd.fraud_transaction_detector.aml.deployment.domain;

import java.time.Instant;

public record ActiveModelPointer(
        String modelType,
        String modelSegment,
        String activeModelVersion,
        String previousModelVersion,
        long pointerVersion,
        String activatedBy,
        Instant activatedAt
) {
}
