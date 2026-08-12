package com.ftd.fraud_transaction_detector.aml.training.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DatasetManifest(
        UUID trainingRunId,
        String featureVersion,
        String modelType,
        String modelSegment,
        LocalDate fromBusinessDate,
        LocalDate toBusinessDate,
        LocalDateTime cutoffTimestamp,
        long rowCount,
        String datasetChecksum,
        List<String> columns,
        List<String> modelFeatureColumns,
        List<PartFile> files,
        Instant generatedAt
) {
    public record PartFile(String path, long rowCount, long sizeBytes, String sha256) {
    }
}
