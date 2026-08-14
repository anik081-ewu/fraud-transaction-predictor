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
        int localOutlierFactorMaxTrainingRows,
        int localOutlierFactorNeighbors,
        double localOutlierFactorContamination,
        int randomSeed,
        String learningMode,
        Map<String, Object> hyperparams
) {
    public GrowthAnalysisRequest(
            String datasetPath, String datasetChecksum, List<Integer> percentages, int minimumRows,
            double holdoutFraction, int maximumEvaluationRows, int isolationForestMaximumTrainingRows,
            int isolationForestEstimators, int autoencoderMaxTrainingRows,
            int localOutlierFactorMaxTrainingRows, int localOutlierFactorNeighbors,
            double localOutlierFactorContamination, int randomSeed
    ) {
        this(datasetPath, datasetChecksum, percentages, minimumRows, holdoutFraction,
                maximumEvaluationRows, isolationForestMaximumTrainingRows,
                isolationForestEstimators, autoencoderMaxTrainingRows,
                localOutlierFactorMaxTrainingRows, localOutlierFactorNeighbors,
                localOutlierFactorContamination, randomSeed, "UNSUPERVISED", Map.of());
    }
}
