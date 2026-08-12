package com.ftd.fraud_transaction_detector.fraud.web.dto;

public record AlertReviewRequest(
        String reviewedBy,
        String strFileName
) {
}
