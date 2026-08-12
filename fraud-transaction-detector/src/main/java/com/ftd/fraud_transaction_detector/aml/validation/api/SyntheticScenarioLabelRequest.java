package com.ftd.fraud_transaction_detector.aml.validation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SyntheticScenarioLabelRequest(
        @NotBlank String transactionId,
        @NotBlank String scenarioCode,
        @NotNull Boolean expectedSuspicious,
        @NotBlank String labeledBy
) {
}
