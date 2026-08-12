package com.ftd.fraud_transaction_detector.comparison.dto;

import com.ftd.fraud_transaction_detector.comparison.entity.ComparisonRun;

import java.time.Instant;

public record ComparisonRunResponse(
        Long id,
        String comparisonRunNo,
        Long uploadedDatasetId,
        Long scenarioSetId,
        String selectedPartitionSizes,
        String selectedModels,
        String runStatus,
        String requestedBy,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt
) {
    public static ComparisonRunResponse from(ComparisonRun run) {
        return new ComparisonRunResponse(
                run.getId(),
                run.getComparisonRunNo(),
                run.getUploadedDataset().getId(),
                run.getScenarioSet().getId(),
                run.getSelectedPartitionSizes(),
                run.getSelectedModels(),
                run.getRunStatus(),
                run.getRequestedBy(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getCreatedAt()
        );
    }
}
