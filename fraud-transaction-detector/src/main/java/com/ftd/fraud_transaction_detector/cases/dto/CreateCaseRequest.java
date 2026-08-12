package com.ftd.fraud_transaction_detector.cases.dto;

public record CreateCaseRequest(Long fraudAlertId, String transactionId, String accountId, String title,
                                String priority, String assignedTo, String createdBy) {
}
