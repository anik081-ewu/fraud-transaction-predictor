package com.ftd.fraud_transaction_detector.aml.deployment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ModelDeploymentRequest(
        @NotNull UUID actionId,
        @NotBlank @Size(max = 500) String reason
) {
}
