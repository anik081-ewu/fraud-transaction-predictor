package com.ftd.fraud_transaction_detector.aml.research.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One run of the data-growth analysis over a training snapshot.
 *
 * @param status QUEUED, RUNNING, COMPLETED or FAILED
 */
public record GrowthStudy(
        UUID studyId,
        UUID trainingRunId,
        String status,
        String featureVersion,
        Long datasetRows,
        Integer featureCount,
        List<Integer> partitionPercentages,
        String methodologyJson,
        String requestedBy,
        String failureReason,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        List<GrowthMetric> metrics
) {
}
