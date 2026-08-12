package com.ftd.fraud_transaction_detector.aml.training.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FailModelTrainingRequest(
        @NotBlank @Size(max = 4000) String reason
) {
}
