package com.ftd.fraud_transaction_detector.aml.deployment.domain;

import java.time.Instant;
import java.util.UUID;

public record LayeredDeploymentPointer(
        String peerGroupCode,
        String deploymentMode,
        String riskPolicyVersion,
        String hstModelVersion,
        String onlineOcSvmModelVersion,
        UUID validationId,
        int canaryPercentage,
        long pointerVersion,
        String activatedBy,
        Instant activatedAt
) {
}
