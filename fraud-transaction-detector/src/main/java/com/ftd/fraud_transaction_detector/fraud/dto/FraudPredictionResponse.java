package com.ftd.fraud_transaction_detector.fraud.dto;

import java.util.List;
import java.util.Map;

public record FraudPredictionResponse(
        String transactionId,
        String accountId,
        boolean suspicious,
        String riskLevel,
        int anomalyVotes,
        Map<String, Object> modelResults,
        Map<String, Object> featureSummary,
        List<String> reasons,
        String recommendedAction
) {
}

