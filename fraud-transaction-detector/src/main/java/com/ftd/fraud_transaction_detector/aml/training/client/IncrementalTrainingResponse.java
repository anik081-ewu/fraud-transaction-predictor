package com.ftd.fraud_transaction_detector.aml.training.client;

import java.util.Map;

public record IncrementalTrainingResponse(
        String status,
        String modelVersion,
        String artifactPath,
        String artifactChecksum,
        String featureSchemaChecksum,
        long learnedRowCount,
        double anomalyRate,
        long validationRowCount,
        long alertCount,
        double averageScore,
        double scoreP95,
        double scoreP99,
        double threshold,
        double trainingDurationMs,
        Map<String, Object> parameters,
        Map<String, Object> metrics
) {
}
