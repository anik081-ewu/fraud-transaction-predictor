package com.ftd.fraud_transaction_detector.aml.training.api;

import java.util.Map;

/**
 * Registration payload for a batch model whose artifacts were produced by the shared
 * /train call rather than by a per-model candidate bundle.
 *
 * Unlike {@link RegisterCandidateModelRequest} there is no per-model artifact directory or
 * artifact-manifest.json: the three batch models are written side by side into one shared
 * bundle directory. Registering that directory keeps the registry row describing the exact
 * artifacts that serve live scoring, at the cost of the per-model bundle validation the
 * incremental path performs.
 */
public record RegisterBatchCandidateRequest(
        String modelVersion,
        String artifactPath,
        long learnedRowCount,
        Double anomalyRate,
        Long validationRowCount,
        Long alertCount,
        Double averageScore,
        Double scoreP95,
        Double scoreP99,
        Map<String, Object> parameters,
        Map<String, Object> metrics,
        String registeredBy
) {
}
