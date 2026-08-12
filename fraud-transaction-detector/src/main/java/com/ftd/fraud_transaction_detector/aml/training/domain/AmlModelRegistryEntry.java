package com.ftd.fraud_transaction_detector.aml.training.domain;

import java.time.Instant;
import java.util.UUID;

public record AmlModelRegistryEntry(
        String modelVersion,
        String modelType,
        String modelSegment,
        String featureVersion,
        UUID trainingRunId,
        String artifactPath,
        String artifactChecksum,
        String datasetChecksum,
        String baseModelVersion,
        String featureSchemaChecksum,
        String status,
        Long artifactSizeBytes,
        Long learnedRowCount,
        Double anomalyRate,
        Long validationRowCount,
        Long alertCount,
        Double averageScore,
        Double scoreP95,
        Double scoreP99,
        String parametersJson,
        String metricsJson,
        String registeredBy,
        Instant createdAt
) {
}
