package com.ftd.fraud_transaction_detector.uploads.web;

import com.ftd.fraud_transaction_detector.uploads.entity.BulkUploadBatch;
import com.ftd.fraud_transaction_detector.uploads.repo.BulkUploadBatchRepository;
import com.ftd.fraud_transaction_detector.uploads.service.ExcelBulkUploadService;
import com.ftd.fraud_transaction_detector.uploads.web.dto.BatchSummaryResponse;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/uploads/batches")
public class BatchesController {

    private final BulkUploadBatchRepository batchRepository;
    private final ExcelBulkUploadService uploadService;

    public BatchesController(BulkUploadBatchRepository batchRepository, ExcelBulkUploadService uploadService) {
        this.batchRepository = batchRepository;
        this.uploadService = uploadService;
    }

    @GetMapping
    public List<BatchSummaryResponse> listBatches() {
        return batchRepository.findAll(Sort.by(Sort.Direction.DESC, "id"))
                .stream()
                .map(BatchesController::toDto)
                .toList();
    }

    @GetMapping("/latest")
    public BatchSummaryResponse getLatestBatch() {
        return uploadService.getLatestCompletedBatch();
    }

    @GetMapping("/{batchNo}")
    public BatchSummaryResponse getBatch(@PathVariable String batchNo) {
        return uploadService.getBatchSummary(batchNo);
    }

    private static BatchSummaryResponse toDto(BulkUploadBatch batch) {
        return new BatchSummaryResponse(
                batch.getBatchNo(),
                batch.getFileName(),
                batch.getTotalRows(),
                batch.getSuccessRows(),
                batch.getFailedRows(),
                batch.getUploadedBy(),
                batch.getUploadedAt(),
                batch.getStatus(),
                null,
                null
        );
    }
}

