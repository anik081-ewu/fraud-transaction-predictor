package com.ftd.fraud_transaction_detector.fraud.dto;

import java.util.List;
import java.util.Map;

public record PersistedFeaturePredictRequest(
        String transactionId,
        String accountId,
        String featureVersion,
        String modelFeatureSchema,
        Map<String, Double> features,
        Map<String, Object> featureSummary,
        List<String> reasons,
        String modelsDir,
        List<String> modelNames,
        String activeModelsDir,
        String activeModelVersion,
        String challengerModelsDir,
        String challengerModelVersion,
        String shadowOnlineSvmDir,
        String shadowOnlineSvmVersion
) {
}
