package com.ftd.fraud_transaction_detector.aml.model.domain;

import com.ftd.fraud_transaction_detector.aml.scoring.domain.NormalizedScore;
import com.ftd.fraud_transaction_detector.aml.scoring.domain.RiskBand;

import java.util.List;

public record MlModelScore(
        String modelType,
        String modelVersion,
        NormalizedScore score,
        boolean anomaly,
        RiskBand riskBand,
        List<String> reasonCodes
) {
    public MlModelScore {
        if (modelType == null || modelType.isBlank()) throw new IllegalArgumentException("modelType is required");
        if (modelVersion == null || modelVersion.isBlank()) throw new IllegalArgumentException("modelVersion is required");
        if (score == null) throw new IllegalArgumentException("score is required");
        if (riskBand == null) throw new IllegalArgumentException("riskBand is required");
        modelType = modelType.trim();
        modelVersion = modelVersion.trim();
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }
}
