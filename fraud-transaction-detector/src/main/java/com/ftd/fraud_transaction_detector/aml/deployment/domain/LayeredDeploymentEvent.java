package com.ftd.fraud_transaction_detector.aml.deployment.domain;

import java.time.Instant;
import java.util.UUID;

public record LayeredDeploymentEvent(
        UUID deploymentId,
        UUID actionId,
        String deploymentAction,
        String peerGroupCode,
        String previousMode,
        String activatedMode,
        String riskPolicyVersion,
        String hstModelVersion,
        String onlineOcSvmModelVersion,
        UUID validationId,
        Integer previousCanaryPercentage,
        int activatedCanaryPercentage,
        String reason,
        String performedBy,
        Instant performedAt
) {
}
