package com.ftd.fraud_transaction_detector.aml.training.api;

import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record RunTrainingPipelineRequest(
        @NotBlank String featureVersion,
        String modelSegment,
        @NotNull LocalDate fromBusinessDate,
        @NotNull LocalDate toBusinessDate,
        @NotNull LocalDateTime cutoffTimestamp,
        String requestedBy,
        String learningMode,
        List<String> selectedModels
) {
    public CreateTrainingRunRequest toCreateRequest(String activeLearningMode) {
        String snapshotModelType = "SUPERVISED".equalsIgnoreCase(activeLearningMode)
                ? "SUPERVISED_ENSEMBLE"
                : "UNSUPERVISED_ENSEMBLE";
        return new CreateTrainingRunRequest(
                AmlTrainingType.FULL_REBUILD,
                featureVersion,
                snapshotModelType,
                modelSegment,
                fromBusinessDate,
                toBusinessDate,
                cutoffTimestamp
        );
    }
}
