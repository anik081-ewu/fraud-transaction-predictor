package com.ftd.fraud_transaction_detector.transactions.web.dto;

import java.util.List;
import java.util.Map;

public record CreateTransactionResponse(
        String transactionId,
        String accountId,
        String riskLevel,
        boolean suspicious,
        int anomalyVotes,
        Map<String, Object> modelResults,
        Map<String, Object> featureSummary,
        List<String> reasons,
        String recommendedAction
) {
}
