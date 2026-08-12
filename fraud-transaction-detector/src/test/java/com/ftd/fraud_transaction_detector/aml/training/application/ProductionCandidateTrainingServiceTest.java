package com.ftd.fraud_transaction_detector.aml.training.application;

import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingRun;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingType;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.AmlTrainingRunRepository;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionCandidateTrainingServiceTest {

    @Test
    void startsBothEnabledModelsFromOneSnapshot() {
        UUID snapshotId = UUID.randomUUID();
        UUID onlineRunId = UUID.randomUUID();
        AmlTrainingRunRepository repository = mock(AmlTrainingRunRepository.class);
        IncrementalTrainingService trainingService = mock(IncrementalTrainingService.class);
        AppConfigService configService = mock(AppConfigService.class);
        AmlTrainingRun snapshot = run(snapshotId, "HALF_SPACE_TREES", "DATASET_READY");
        AmlTrainingRun online = run(onlineRunId, "ONLINE_ONE_CLASS_SVM", "DATASET_READY");
        AmlTrainingRun hstTraining = run(snapshotId, "HALF_SPACE_TREES", "TRAINING");
        AmlTrainingRun onlineTraining = run(onlineRunId, "ONLINE_ONE_CLASS_SVM", "TRAINING");
        when(repository.findRequired(snapshotId)).thenReturn(snapshot);
        when(repository.createReadySibling(snapshot, "ONLINE_ONE_CLASS_SVM")).thenReturn(online);
        when(configService.isHstEnabled(true)).thenReturn(true);
        when(configService.isOnlineOneClassSvmEnabled(true)).thenReturn(true);
        when(trainingService.start(snapshotId, null)).thenReturn(hstTraining);
        when(trainingService.start(onlineRunId, null)).thenReturn(onlineTraining);

        List<AmlTrainingRun> started = new ProductionCandidateTrainingService(
                repository, trainingService, configService).start(snapshotId);

        assertEquals(List.of(hstTraining, onlineTraining), started);
        verify(repository).createReadySibling(snapshot, "ONLINE_ONE_CLASS_SVM");
        verify(trainingService).start(snapshotId, null);
        verify(trainingService).start(onlineRunId, null);
    }

    private AmlTrainingRun run(UUID id, String modelType, String status) {
        return new AmlTrainingRun(
                id, AmlTrainingType.BACKTEST, "AML_FEATURES_V2", modelType, null,
                LocalDate.of(2023, 1, 1), LocalDate.of(2024, 1, 1),
                LocalDateTime.of(2024, 1, 1, 23, 59), 2512L, 2512L, null,
                "outputs/dataset", "d".repeat(64), null, null, status, null,
                null, null, Instant.parse("2026-08-06T00:00:00Z")
        );
    }
}
