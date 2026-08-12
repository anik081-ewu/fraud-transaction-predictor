package com.ftd.fraud_transaction_detector.aml.training.api;

import jakarta.validation.constraints.Size;

public record RunIncrementalTrainingRequest(
        @Size(max = 100) String baseModelVersion,
        @Size(max = 100) String requestedBy
) {
}
