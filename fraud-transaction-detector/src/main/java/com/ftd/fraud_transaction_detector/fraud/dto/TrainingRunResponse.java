package com.ftd.fraud_transaction_detector.fraud.dto;

import com.ftd.fraud_transaction_detector.fraud.entity.TrainingRun;

import java.time.Instant;

public record TrainingRunResponse(
        Long id,
        String runNo,
        String source,
        String requestedBy,
        String status,
        Integer trainingRowCount,
        Integer featureCount,
        String responseStatus,
        String message,
        String modelsJson,
        String artifactsJson,
        String hyperparamsJson,
        Instant startedAt,
        Instant completedAt,
        Long durationMs
) {
    public static TrainingRunResponse from(TrainingRun run) {
        return new TrainingRunResponse(
                run.getId(),
                run.getRunNo(),
                run.getSource(),
                run.getRequestedBy(),
                run.getStatus(),
                run.getTrainingRowCount(),
                run.getFeatureCount(),
                run.getResponseStatus(),
                run.getMessage(),
                run.getModelsJson(),
                run.getArtifactsJson(),
                run.getHyperparamsJson(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getDurationMs()
        );
    }
}
