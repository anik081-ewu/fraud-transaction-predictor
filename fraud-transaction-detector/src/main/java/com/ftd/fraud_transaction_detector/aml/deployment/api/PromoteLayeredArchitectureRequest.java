package com.ftd.fraud_transaction_detector.aml.deployment.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PromoteLayeredArchitectureRequest(
        @NotNull UUID actionId,
        @NotNull UUID validationId,
        @NotBlank String peerGroupCode,
        @Min(1) @Max(100) int canaryPercentage,
        @NotBlank String reason
) {
}
