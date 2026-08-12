package com.ftd.fraud_transaction_detector.aml.training.client;

import java.util.Map;

public record IncrementalTrainingRequest(
        String trainingRunId,
        String datasetPath,
        String datasetChecksum,
        String artifactBasePath,
        String modelVersion,
        String modelType,
        String modelSegment,
        String featureVersion,
        String baseModelPath,
        Map<String, Object> parameters
) {
}
