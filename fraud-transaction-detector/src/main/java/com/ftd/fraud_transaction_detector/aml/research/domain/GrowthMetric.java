package com.ftd.fraud_transaction_detector.aml.research.domain;

/**
 * One detector measured at one data partition.
 *
 * @param excessMassAuc      label-free detector quality in [0,1]; the only score here that
 *                           is comparable across detectors
 * @param boundedTrainingSample true when the detector hit its training-row cap, so this
 *                           cell did not actually learn from the full partition and must
 *                           not be read as a like-for-like point on the growth curve
 */
public record GrowthMetric(
        String detector,
        int partitionPercentage,
        Long partitionRows,
        Long trainingRows,
        Long learnedRows,
        Long evaluationRows,
        Double excessMassAuc,
        Double scoreSkewness,
        Double rankStability,
        Double anomalyRate,
        Long alertCount,
        Double threshold,
        Double averageScore,
        Double scoreP50,
        Double scoreP95,
        Double scoreP99,
        Double trainingDurationMs,
        Double rowsPerSecond,
        boolean boundedTrainingSample
) {
}
