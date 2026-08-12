package com.ftd.fraud_transaction_detector.uploads.web.dto;

import java.time.Instant;
import java.time.LocalDate;

public record BatchSummaryResponse(
        String batchNo,
        String fileName,
        Integer totalRows,
        Integer successRows,
        Integer failedRows,
        String uploadedBy,
        Instant uploadedAt,
        String status,
        LocalDate minTransactionDate,
        LocalDate maxTransactionDate
) {
}

