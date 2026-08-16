package com.ftd.fraud_transaction_detector.aml.training.application;

import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingRun;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingType;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.AmlTrainingRunRepository;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import com.ftd.fraud_transaction_detector.fraud.client.ModelTrainingClient;
import com.ftd.fraud_transaction_detector.fraud.dto.TrainModelResponse;
import com.ftd.fraud_transaction_detector.transactions.entity.Transaction;
import com.ftd.fraud_transaction_detector.transactions.repo.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;
import com.ftd.fraud_transaction_detector.fraud.dto.TrainModelRequest;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProductionCandidateTrainingServiceTest {

    @Test
    void trainsSelectedModelsFromOneSnapshot() {
        UUID snapshotId = UUID.randomUUID();
        AmlTrainingRunRepository repository = mock(AmlTrainingRunRepository.class);
        AppConfigService configService = mock(AppConfigService.class);
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        ModelTrainingClient trainingClient = mock(ModelTrainingClient.class);
        AmlTrainingRun snapshot = run(snapshotId, "UNSUPERVISED_ENSEMBLE", "DATASET_READY");
        when(repository.findRequired(snapshotId)).thenReturn(snapshot);
        when(configService.getLearningMode()).thenReturn("SUPERVISED");
        when(configService.isSupervisedLearningMode()).thenReturn(true);
        when(configService.isModelTrainingEnabled(anyString(), eq(true))).thenReturn(true);
        when(trainingClient.train(org.mockito.ArgumentMatchers.any())).thenReturn(new TrainModelResponse(
                "SUCCESS", "trained", 1, 2, List.of("XGBoost"), java.util.Map.of(),
                "models/run", java.util.Map.of("XGBoost", java.util.Map.of())
        ));
        ProductionCandidateTrainingService service = new ProductionCandidateTrainingService(
                repository,
                configService,
                transactionRepository,
                trainingClient,
                mock(BatchCandidateRegistrar.class)
        );

        var response = service.start(snapshotId, List.of("XGBOOST_CLASSIFIER"), "test-operator");

        assertEquals(List.of("XGBOOST_CLASSIFIER"), response.trainedModels());
        assertEquals("SUCCESS", response.trainingStatus());
        ArgumentCaptor<TrainModelRequest> request = ArgumentCaptor.forClass(TrainModelRequest.class);
        verify(trainingClient).train(request.capture());
        assertEquals(snapshot.datasetPath(), request.getValue().datasetPath());
        assertEquals(snapshot.datasetChecksum(), request.getValue().datasetChecksum());
        assertNull(request.getValue().transactions());
    }

    @Test
    void keepsRawTransactionCompatibilityForUnsupervisedTraining() {
        UUID snapshotId = UUID.randomUUID();
        AmlTrainingRunRepository repository = mock(AmlTrainingRunRepository.class);
        AppConfigService configService = mock(AppConfigService.class);
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        ModelTrainingClient trainingClient = mock(ModelTrainingClient.class);
        AmlTrainingRun snapshot = run(snapshotId, "UNSUPERVISED_ENSEMBLE", "DATASET_READY");
        when(repository.findRequired(snapshotId)).thenReturn(snapshot);
        when(configService.getLearningMode()).thenReturn("UNSUPERVISED");
        when(configService.isModelTrainingEnabled(anyString(), eq(true))).thenReturn(true);
        when(transactionRepository.findEligibleForTraining(
                snapshot.fromBusinessDate(), snapshot.toBusinessDate(), snapshot.cutoffTimestamp()
        )).thenReturn(List.of(mock(Transaction.class)));
        when(trainingClient.train(org.mockito.ArgumentMatchers.any())).thenReturn(new TrainModelResponse(
                "SUCCESS", "trained", 1, 2, List.of("BehavioralClusterOutlier"), java.util.Map.of(),
                "models/run", java.util.Map.of("BehavioralClusterOutlier", java.util.Map.of())
        ));
        ProductionCandidateTrainingService service = new ProductionCandidateTrainingService(
                repository, configService, transactionRepository, trainingClient,
                mock(BatchCandidateRegistrar.class)
        );

        service.start(snapshotId, List.of("BEHAVIORAL_CLUSTER_OUTLIER"), "test-operator");

        ArgumentCaptor<TrainModelRequest> request = ArgumentCaptor.forClass(TrainModelRequest.class);
        verify(trainingClient).train(request.capture());
        assertEquals(1, request.getValue().transactions().size());
        assertNull(request.getValue().datasetPath());
        assertNull(request.getValue().datasetChecksum());
    }

    @Test
    void mapsLegacyLogisticSelectionToExtraTrees() {
        UUID snapshotId = UUID.randomUUID();
        AmlTrainingRunRepository repository = mock(AmlTrainingRunRepository.class);
        AppConfigService configService = mock(AppConfigService.class);
        ModelTrainingClient trainingClient = mock(ModelTrainingClient.class);
        AmlTrainingRun snapshot = run(snapshotId, "SUPERVISED_ENSEMBLE", "DATASET_READY");
        when(repository.findRequired(snapshotId)).thenReturn(snapshot);
        when(configService.getLearningMode()).thenReturn("SUPERVISED");
        when(configService.isSupervisedLearningMode()).thenReturn(true);
        when(configService.isModelTrainingEnabled(anyString(), eq(true))).thenReturn(true);
        when(trainingClient.train(org.mockito.ArgumentMatchers.any())).thenReturn(new TrainModelResponse(
                "SUCCESS", "trained", 1, 2, List.of("ExtraTreesClassifier"), java.util.Map.of(),
                "models/run", java.util.Map.of("ExtraTreesClassifier", java.util.Map.of())
        ));
        ProductionCandidateTrainingService service = new ProductionCandidateTrainingService(
                repository, configService, mock(TransactionRepository.class), trainingClient,
                mock(BatchCandidateRegistrar.class)
        );

        var response = service.start(snapshotId, List.of("LOGISTIC_REGRESSION"), "test-operator");

        assertEquals(List.of("EXTRA_TREES_CLASSIFIER"), response.trainedModels());
        ArgumentCaptor<TrainModelRequest> request = ArgumentCaptor.forClass(TrainModelRequest.class);
        verify(trainingClient).train(request.capture());
        assertEquals(List.of("ExtraTreesClassifier"), request.getValue().modelNames());
    }

    private AmlTrainingRun run(UUID id, String modelType, String status) {
        return new AmlTrainingRun(
                id, AmlTrainingType.BACKTEST, "AML_FEATURES_V2", modelType, null,
                LocalDate.of(2023, 1, 1), LocalDate.of(2024, 1, 1),
                LocalDateTime.of(2024, 1, 1, 23, 59), 2512L, 2512L, null,
                "outputs/dataset", "d".repeat(64), null, null, status, null,
                null, null, Instant.parse("2026-08-06T00:00:00Z"), null, null, null
        );
    }
}
