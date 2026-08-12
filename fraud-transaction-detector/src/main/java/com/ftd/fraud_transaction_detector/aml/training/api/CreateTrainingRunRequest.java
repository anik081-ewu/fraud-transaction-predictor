package com.ftd.fraud_transaction_detector.aml.training.api;

import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CreateTrainingRunRequest(
        @NotNull AmlTrainingType trainingType,
        @NotBlank String featureVersion,
        String modelType,
        String modelSegment,
        @NotNull LocalDate fromBusinessDate,
        @NotNull LocalDate toBusinessDate,
        @NotNull LocalDateTime cutoffTimestamp
) {
}
