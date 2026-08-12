package com.ftd.fraud_transaction_detector.fraud.dto;

import java.util.List;
import java.util.Map;

public record TrainModelResponse(
        String status,
        String message,
        int trainedRows,
        int featureCount,
        List<String> models,
        Map<String, String> artifacts,
        String artifactBasePath,
        Map<String, Map<String, Object>> metrics
) {
}
