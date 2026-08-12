package com.ftd.fraud_transaction_detector.cases.dto;

import java.time.Instant;

public record CasePredictionEvidenceResponse(
        String riskLevel, Integer anomalyVotes, Boolean suspicious,
        String modelVersion, String featureVersion,
        Double incrementalModelScore, Double batchModelScore,
        String reasonCodes, String learningDecision, String learningDecisionReason,
        Instant predictedAt
) {
}
