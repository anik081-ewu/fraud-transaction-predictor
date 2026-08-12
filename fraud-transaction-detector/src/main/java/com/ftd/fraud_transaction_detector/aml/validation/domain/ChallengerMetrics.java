package com.ftd.fraud_transaction_detector.aml.validation.domain;

public record ChallengerMetrics(
        long sampleCount,
        long candidateAnomalyCount,
        long productionAlertCount,
        long overlapCount,
        long candidateOnlyCount,
        long productionOnlyCount,
        double candidateAnomalyRate,
        double productionAlertRate,
        double agreementRate,
        Double alertJaccard,
        Double averageScore,
        Double scoreStandardDeviation,
        Double scoreP50,
        Double scoreP95,
        Double scoreP99,
        Double dailyAnomalyRateStandardDeviation,
        long reviewedOverlapCount,
        long falsePositiveOverlapCount,
        long strOverlapCount,
        Double reviewedPrecision
) {
}
