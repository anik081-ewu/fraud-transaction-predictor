package com.ftd.fraud_transaction_detector.comparison.dto;

import com.ftd.fraud_transaction_detector.comparison.entity.ModelVersion;

import java.time.Instant;

public record ModelVersionResponse(
        Long id,
        String modelVersionNo,
        Long trainingRunId,
        Long datasetPartitionId,
        String modelName,
        Integer partitionSize,
        String artifactBasePath,
        String featureColumnsPath,
        String scalerPath,
        String modelPath,
        String metricsJson,
        Boolean isActive,
        String lifecycleStatus,
        Instant promotedAt,
        String promotedBy,
        Instant createdAt
) {
    public static ModelVersionResponse from(ModelVersion modelVersion) {
        return new ModelVersionResponse(
                modelVersion.getId(),
                modelVersion.getModelVersionNo(),
                modelVersion.getTrainingRun().getId(),
                modelVersion.getDatasetPartition().getId(),
                modelVersion.getModelName(),
                modelVersion.getPartitionSize(),
                modelVersion.getArtifactBasePath(),
                modelVersion.getFeatureColumnsPath(),
                modelVersion.getScalerPath(),
                modelVersion.getModelPath(),
                modelVersion.getMetricsJson(),
                modelVersion.getIsActive(),
                modelVersion.getLifecycleStatus(),
                modelVersion.getPromotedAt(),
                modelVersion.getPromotedBy(),
                modelVersion.getCreatedAt()
        );
    }
}
