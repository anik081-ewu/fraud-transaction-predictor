package com.ftd.fraud_transaction_detector.casework;

public record AutomaticCaseRequested(
        Long fraudAlertId,
        String transactionId,
        String accountId,
        String riskLevel,
        int anomalyVotes
) {
}
