package com.ftd.fraud_transaction_detector.aml.deployment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RollbackLayeredArchitectureRequest(
        @NotNull UUID actionId,
        @NotBlank String peerGroupCode,
        @NotBlank String reason
) {
}
