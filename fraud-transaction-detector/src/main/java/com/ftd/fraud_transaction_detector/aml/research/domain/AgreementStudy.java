package com.ftd.fraud_transaction_detector.aml.research.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * One run of the model-agreement analysis.
 *
 * @param resultJson the full overlap matrix as returned by the ML service; always consumed
 *                   as a whole by the UI, so it is stored rather than shredded into columns
 */
public record AgreementStudy(
        UUID studyId,
        UUID trainingRunId,
        String status,
        Long evaluatedRows,
        Integer modelCount,
        String resultJson,
        String requestedBy,
        String failureReason,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt
) {
}
