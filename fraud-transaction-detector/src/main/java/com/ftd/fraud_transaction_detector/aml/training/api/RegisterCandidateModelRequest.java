package com.ftd.fraud_transaction_detector.aml.training.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record RegisterCandidateModelRequest(
        @NotBlank @Size(max = 100) String modelVersion,
        @NotBlank @Size(max = 1000) String artifactPath,
        @NotBlank @Size(max = 200) String artifactChecksum,
        @NotBlank @Size(max = 200) String featureSchemaChecksum,
        @NotNull @Positive Long learnedRowCount,
        @PositiveOrZero Double anomalyRate,
        @PositiveOrZero Long validationRowCount,
        @PositiveOrZero Long alertCount,
        Double averageScore,
        Double scoreP95,
        Double scoreP99,
        Map<String, Object> parameters,
        Map<String, Object> metrics,
        @NotBlank @Size(max = 100) String registeredBy
) {
}
