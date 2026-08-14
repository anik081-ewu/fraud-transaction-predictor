package com.ftd.fraud_transaction_detector.aml.training.api;

import java.util.List;

public record ProductionCandidateTrainingResponse(
        List<String> trainedModels,
        String trainingStatus,
        String trainingMessage
) {
}
