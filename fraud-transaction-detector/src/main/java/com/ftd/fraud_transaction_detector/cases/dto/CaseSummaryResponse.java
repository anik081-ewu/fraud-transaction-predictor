package com.ftd.fraud_transaction_detector.cases.dto;

import java.time.Instant;

public record CaseSummaryResponse(
        Long id,
        String caseNo,
        Long fraudAlertId,
        String transactionId,
        String accountId,
        String title,
        String status,
        String priority,
        String assignedTo,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
