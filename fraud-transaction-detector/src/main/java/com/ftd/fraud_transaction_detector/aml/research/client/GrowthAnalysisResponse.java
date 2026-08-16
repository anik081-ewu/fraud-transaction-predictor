package com.ftd.fraud_transaction_detector.aml.research.client;

import java.util.List;
import java.util.Map;

public record GrowthAnalysisResponse(
        String status,
        long datasetRows,
        int featureCount,
        String featureVersion,
        List<Integer> partitionPercentages,
        List<String> detectors,
        Map<String, Object> methodology,
        List<Map<String, Object>> results,
        List<Map<String, Object>> ensembles,
        List<Map<String, Object>> riskPolicyEvaluations
) {
}
