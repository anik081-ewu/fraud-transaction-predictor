package com.ftd.fraud_transaction_detector.aml.validation.domain;

public record SegmentShadowMetrics(
        String peerGroupCode,
        long sampleCount,
        long layeredAlertCount,
        double layeredAlertRate,
        Double dailyAlertRateStandardDeviation
) {
}
