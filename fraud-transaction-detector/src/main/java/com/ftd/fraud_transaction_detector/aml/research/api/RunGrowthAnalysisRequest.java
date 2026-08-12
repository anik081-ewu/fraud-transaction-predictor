package com.ftd.fraud_transaction_detector.aml.research.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

public record RunGrowthAnalysisRequest(
        List<@Min(1) @Max(100) Integer> percentages,
        @Min(200) Integer minimumRows,
        @Min(100) Integer maximumEvaluationRows,
        @Min(1_000) Integer isolationForestMaximumTrainingRows
) {
}
