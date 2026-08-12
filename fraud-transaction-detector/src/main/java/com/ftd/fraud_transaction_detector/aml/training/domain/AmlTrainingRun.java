package com.ftd.fraud_transaction_detector.aml.training.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record AmlTrainingRun(
        UUID trainingRunId,
        AmlTrainingType trainingType,
        String featureVersion,
        String modelType,
        String modelSegment,
        LocalDate fromBusinessDate,
        LocalDate toBusinessDate,
        LocalDateTime cutoffTimestamp,
        Long requestedRowCount,
        Long exportedRowCount,
        Long learnedRowCount,
        String datasetPath,
        String datasetChecksum,
        String baseModelVersion,
        String candidateModelVersion,
        String status,
        String failureReason,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        String progressStage,
        Long progressCurrent,
        Long progressTotal
) {
}
