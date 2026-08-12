package com.ftd.fraud_transaction_detector.uploads.web.dto;

import java.util.List;

public record BulkUploadResponse(
        String batchNo,
        String fileName,
        int totalRows,
        int successRows,
        int failedRows,
        List<RowError> errors,
        Long uploadedDatasetId,
        String uploadedDatasetNo
) {
    public record RowError(int rowNumber, String message) {
    }
}
