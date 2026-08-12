package com.ftd.fraud_transaction_detector.aml.training.api;

import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record RunTrainingPipelineRequest(
        @NotBlank String featureVersion,
        String modelSegment,
        @NotNull LocalDate fromBusinessDate,
        @NotNull LocalDate toBusinessDate,
        @NotNull LocalDateTime cutoffTimestamp,
        String requestedBy
) {
    public CreateTrainingRunRequest toCreateRequest() {
        return new CreateTrainingRunRequest(
                AmlTrainingType.FULL_REBUILD,
                featureVersion,
                null,
                modelSegment,
                fromBusinessDate,
                toBusinessDate,
                cutoffTimestamp
        );
    }
}
