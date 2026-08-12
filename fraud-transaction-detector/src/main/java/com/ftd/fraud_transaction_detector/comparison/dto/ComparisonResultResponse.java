package com.ftd.fraud_transaction_detector.comparison.dto;

import com.ftd.fraud_transaction_detector.comparison.entity.ComparisonResult;

import java.time.Instant;

public record ComparisonResultResponse(
        Long id,
        Long comparisonRunId,
        Long scenarioId,
        Long datasetPartitionId,
        Long modelVersionId,
        String modelName,
        Integer rawPrediction,
        Integer anomalyVote,
        String riskLevel,
        Boolean suspicious,
        String recommendedAction,
        Double scoreValue,
        Double decisionValue,
        String reasonsJson,
        String responseJson,
        Long predictionDurationMs,
        Instant createdAt
) {
    public static ComparisonResultResponse from(ComparisonResult result) {
        return new ComparisonResultResponse(
                result.getId(),
                result.getComparisonRun().getId(),
                result.getScenario().getId(),
                result.getDatasetPartition().getId(),
                result.getModelVersion().getId(),
                result.getModelName(),
                result.getRawPrediction(),
                result.getAnomalyVote(),
                result.getRiskLevel(),
                result.getSuspicious(),
                result.getRecommendedAction(),
                result.getScoreValue(),
                result.getDecisionValue(),
                result.getReasonsJson(),
                result.getResponseJson(),
                result.getPredictionDurationMs(),
                result.getCreatedAt()
        );
    }
}
