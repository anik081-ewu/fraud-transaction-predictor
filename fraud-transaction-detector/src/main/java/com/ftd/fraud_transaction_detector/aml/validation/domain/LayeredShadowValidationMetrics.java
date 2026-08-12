package com.ftd.fraud_transaction_detector.aml.validation.domain;

import java.util.List;

public record LayeredShadowValidationMetrics(
        long sampleCount,
        int observationDays,
        long legacyAlertCount,
        long layeredAlertCount,
        long overlapCount,
        long layeredOnlyCount,
        long legacyOnlyCount,
        double legacyAlertRate,
        double layeredAlertRate,
        Double alertVolumeChangeRate,
        double agreementRate,
        Double alertJaccard,
        long topRiskCount,
        long topRiskOverlapCount,
        Double topRiskOverlapRate,
        Double averageLayeredScore,
        Double layeredScoreStandardDeviation,
        Double layeredScoreP50,
        Double layeredScoreP95,
        Double layeredScoreP99,
        Double dailyLayeredAlertRateStandardDeviation,
        List<SegmentShadowMetrics> segmentMetrics,
        Double maxSegmentDailyAlertRateStandardDeviation,
        long syntheticExpectedSuspiciousCount,
        long syntheticDetectedCount,
        Double syntheticScenarioRecall,
        List<SyntheticScenarioMetrics> syntheticScenarioMetrics,
        long reviewedLayeredAlertCount,
        long reviewedTruePositiveCount,
        long reviewedFalsePositiveCount,
        Double reviewedPrecision,
        Double reviewedFalsePositiveRate,
        Double averagePredictionLatencyMs,
        Double predictionLatencyP95Ms,
        long incrementalUpdateCount,
        Double averageIncrementalUpdateMs,
        Double maximumIncrementalUpdateMs,
        double hstAvailabilityRate,
        double onlineOcSvmAvailabilityRate,
        String hstModelVersion,
        int distinctHstModelVersionCount,
        String onlineOcSvmModelVersion,
        int distinctOnlineOcSvmModelVersionCount
) {
    public LayeredShadowValidationMetrics {
        segmentMetrics = segmentMetrics == null ? List.of() : List.copyOf(segmentMetrics);
        syntheticScenarioMetrics = syntheticScenarioMetrics == null
                ? List.of() : List.copyOf(syntheticScenarioMetrics);
    }
}
