package com.ftd.fraud_transaction_detector.aml.research.domain;

import java.time.Instant;
import java.util.UUID;

public record SupervisedGrowthStudy(
        UUID studyId,
        UUID trainingRunId,
        String status,
        String resultJson,
        String requestedBy,
        String failureReason,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt
) {
}
