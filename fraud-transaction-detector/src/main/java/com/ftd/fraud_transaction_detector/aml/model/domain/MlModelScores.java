package com.ftd.fraud_transaction_detector.aml.model.domain;

import java.util.Map;

public record MlModelScores(Map<String, MlModelScore> scores) {
    public MlModelScores {
        scores = scores == null ? Map.of() : Map.copyOf(scores);
    }

    public MlModelScore get(String modelType) {
        return scores.get(modelType);
    }
}
