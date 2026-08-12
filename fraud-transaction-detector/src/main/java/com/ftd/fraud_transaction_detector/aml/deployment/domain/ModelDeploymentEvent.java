package com.ftd.fraud_transaction_detector.aml.deployment.domain;

import java.time.Instant;
import java.util.UUID;

public record ModelDeploymentEvent(
        UUID deploymentId,
        UUID actionId,
        String deploymentAction,
        String modelType,
        String modelSegment,
        String previousModelVersion,
        String activatedModelVersion,
        String reason,
        String performedBy,
        Instant performedAt
) {
}
