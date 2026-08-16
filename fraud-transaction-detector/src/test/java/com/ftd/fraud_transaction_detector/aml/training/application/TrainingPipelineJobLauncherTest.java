package com.ftd.fraud_transaction_detector.aml.training.application;

import com.ftd.fraud_transaction_detector.aml.training.infrastructure.AmlTrainingRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TrainingPipelineJobLauncherTest {

    @Mock
    private TrainingDatasetExportService exportService;
    @Mock
    private ProductionCandidateTrainingService candidateTrainingService;
    @Mock
    private AmlTrainingRunRepository runRepository;

    @Test
    void persistsTheActualTrainingFailureOnTheSnapshotRun() {
        UUID runId = UUID.randomUUID();
        doThrow(new IllegalStateException("ML service unavailable: connection refused"))
                .when(candidateTrainingService).start(runId, List.of("XGBOOST_CLASSIFIER"), "admin");

        new TrainingPipelineJobLauncher(exportService, candidateTrainingService, runRepository)
                .exportThenTrain(runId, "admin", List.of("XGBOOST_CLASSIFIER"));

        verify(runRepository).markPipelineTrainingFailed(
                runId,
                "ML service unavailable: connection refused"
        );
    }
}
