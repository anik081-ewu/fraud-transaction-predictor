package com.ftd.fraud_transaction_detector.comparison.dto;

import com.ftd.fraud_transaction_detector.comparison.entity.UploadedDataset;

import java.time.Instant;
import java.util.List;

public record UploadedDatasetResponse(
        Long id,
        String datasetNo,
        String fileName,
        Integer totalRows,
        Long sourceBatchId,
        String sourceType,
        Long snapshotMaxTransactionId,
        String uploadedBy,
        Instant uploadedAt,
        String status,
        String notes,
        List<DatasetPartitionResponse> partitions
) {
    public static UploadedDatasetResponse from(UploadedDataset dataset, List<DatasetPartitionResponse> partitions) {
        return new UploadedDatasetResponse(
                dataset.getId(),
                dataset.getDatasetNo(),
                dataset.getFileName(),
                dataset.getTotalRows(),
                dataset.getSourceBatchId(),
                dataset.getSourceType(),
                dataset.getSnapshotMaxTransactionId(),
                dataset.getUploadedBy(),
                dataset.getUploadedAt(),
                dataset.getStatus(),
                dataset.getNotes(),
                partitions
        );
    }
}
