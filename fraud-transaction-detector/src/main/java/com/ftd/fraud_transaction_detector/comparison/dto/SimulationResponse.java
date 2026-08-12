package com.ftd.fraud_transaction_detector.comparison.dto;

import java.util.List;
import java.util.Map;

public record SimulationResponse(
        String transactionId,
        String accountId,
        String configName,
        boolean coldStartApplied,
        boolean suspicious,
        String riskLevel,
        int anomalyVotes,
        Map<String, Map<String, Object>> modelResults,
        Map<String, Object> featureSummary,
        List<String> reasons,
        String recommendedAction
) {
}
