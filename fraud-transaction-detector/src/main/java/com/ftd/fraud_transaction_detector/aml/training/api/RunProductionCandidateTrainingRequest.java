package com.ftd.fraud_transaction_detector.aml.training.api;

import jakarta.validation.constraints.Size;

import java.util.List;

public record RunProductionCandidateTrainingRequest(
        @Size(max = 100) String requestedBy,
        List<String> selectedModels
) {
}
