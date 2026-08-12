package com.ftd.fraud_transaction_detector.aml.training.api;

import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingRun;

import java.util.List;

public record ProductionCandidateTrainingResponse(
        List<AmlTrainingRun> trainingRuns,
        List<String> incrementalModels,
        List<String> batchModels,
        String batchTrainingStatus,
        String batchTrainingMessage
) {
}
