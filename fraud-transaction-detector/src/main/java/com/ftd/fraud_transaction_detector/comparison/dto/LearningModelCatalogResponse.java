package com.ftd.fraud_transaction_detector.comparison.dto;

import java.util.List;

public record LearningModelCatalogResponse(
        String mode,
        boolean labelsRequired,
        List<String> primaryMetrics,
        List<LearningModelResponse> models
) {
    public record LearningModelResponse(
            String modelKey,
            String displayName,
            String trainingStyle,
            boolean recommended,
            String purpose
    ) {
    }
}
