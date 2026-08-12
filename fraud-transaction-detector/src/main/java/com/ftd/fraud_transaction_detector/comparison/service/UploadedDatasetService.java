package com.ftd.fraud_transaction_detector.comparison.service;

import com.ftd.fraud_transaction_detector.comparison.dto.DatasetPartitionResponse;
import com.ftd.fraud_transaction_detector.comparison.dto.UploadedDatasetResponse;
import com.ftd.fraud_transaction_detector.comparison.entity.UploadedDataset;
import com.ftd.fraud_transaction_detector.comparison.repo.UploadedDatasetRepository;
import com.ftd.fraud_transaction_detector.transactions.repo.TransactionRepository;
import com.ftd.fraud_transaction_detector.uploads.entity.BulkUploadBatch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class UploadedDatasetService {

    private final UploadedDatasetRepository uploadedDatasetRepository;
    private final DatasetPartitionService datasetPartitionService;
    private final TransactionRepository transactionRepository;

    public UploadedDatasetService(
            UploadedDatasetRepository uploadedDatasetRepository,
            DatasetPartitionService datasetPartitionService,
            TransactionRepository transactionRepository
    ) {
        this.uploadedDatasetRepository = uploadedDatasetRepository;
        this.datasetPartitionService = datasetPartitionService;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public UploadedDatasetResponse registerSuccessfulUpload(BulkUploadBatch batch) {
        UploadedDataset existing = uploadedDatasetRepository.findBySourceBatchId(batch.getId()).orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        UploadedDataset dataset = new UploadedDataset();
        dataset.setDatasetNo("DS-" + Instant.now().toEpochMilli());
        dataset.setFileName(batch.getFileName());
        dataset.setTotalRows(batch.getSuccessRows() == null ? 0 : batch.getSuccessRows());
        dataset.setSourceBatchId(batch.getId());
        dataset.setSourceType("UPLOAD_BATCH");
        dataset.setUploadedBy(batch.getUploadedBy());
        dataset.setUploadedAt(batch.getUploadedAt() == null ? Instant.now() : batch.getUploadedAt());
        dataset.setStatus("ACTIVE");
        dataset.setNotes(buildNotes(batch));

        UploadedDataset saved = uploadedDatasetRepository.save(dataset);
        datasetPartitionService.createPartitionsFor(saved);
        return toResponse(saved);
    }

    @Transactional
    public UploadedDatasetResponse createDatabaseSnapshot(String requestedBy) {
        Long maximumTransactionId = transactionRepository.findMaximumId()
                .orElseThrow(() -> new IllegalArgumentException("No transactions are available for training"));
        long totalRows = transactionRepository.countByIdLessThanEqual(maximumTransactionId);
        if (totalRows < 200) {
            throw new IllegalArgumentException(
                    "At least 200 transactions are required for training. Current database rows: " + totalRows
            );
        }
        if (totalRows > Integer.MAX_VALUE) {
            throw new IllegalStateException("Transaction count exceeds the supported snapshot size");
        }

        Instant now = Instant.now();
        UploadedDataset dataset = new UploadedDataset();
        dataset.setDatasetNo("DB-" + now.toEpochMilli());
        dataset.setFileName("Database snapshot");
        dataset.setTotalRows((int) totalRows);
        dataset.setSourceBatchId(null);
        dataset.setSourceType("DATABASE_SNAPSHOT");
        dataset.setSnapshotMaxTransactionId(maximumTransactionId);
        dataset.setUploadedBy(requestedBy == null || requestedBy.isBlank() ? "comparison-ui" : requestedBy.trim());
        dataset.setUploadedAt(now);
        dataset.setStatus("ACTIVE");
        int holdoutRows = DatasetPartitionService.commonHoldoutRows((int) totalRows);
        dataset.setNotes(
                "Frozen database snapshot through transaction row ID " + maximumTransactionId
                        + ". The newest " + holdoutRows + " rows are reserved as one shared future holdout."
        );

        UploadedDataset saved = uploadedDatasetRepository.save(dataset);
        datasetPartitionService.createPartitionsFor(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<UploadedDatasetResponse> listDatasets() {
        return uploadedDatasetRepository.findAllByOrderByUploadedAtDescIdDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UploadedDatasetResponse getDataset(Long datasetId) {
        UploadedDataset dataset = uploadedDatasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Uploaded dataset not found: " + datasetId));
        return toResponse(dataset);
    }

    private UploadedDatasetResponse toResponse(UploadedDataset dataset) {
        List<DatasetPartitionResponse> partitions = datasetPartitionService.listPartitions(dataset.getId())
                .stream()
                .map(DatasetPartitionResponse::from)
                .toList();
        return UploadedDatasetResponse.from(dataset, partitions);
    }

    private static String buildNotes(BulkUploadBatch batch) {
        Integer failedRows = batch.getFailedRows();
        if (failedRows == null || failedRows <= 0) {
            return null;
        }
        return "Imported with " + failedRows + " failed rows excluded from comparison dataset.";
    }
}
