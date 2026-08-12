package com.ftd.fraud_transaction_detector.aml.research.client;

import java.util.List;
import java.util.Map;

public record GrowthAnalysisRequest(
        String datasetPath,
        String datasetChecksum,
        List<Integer> percentages,
        int minimumRows,
        double holdoutFraction,
        int maximumEvaluationRows,
        int isolationForestMaximumTrainingRows,
        int isolationForestEstimators,
        int autoencoderMaxTrainingRows,
        int randomSeed,
        Map<String, Object> halfSpaceTreesParameters,
        Map<String, Object> onlineOneClassSvmParameters
) {
}
