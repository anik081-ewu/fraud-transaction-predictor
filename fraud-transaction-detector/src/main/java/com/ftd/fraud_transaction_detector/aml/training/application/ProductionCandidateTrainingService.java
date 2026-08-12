package com.ftd.fraud_transaction_detector.aml.training.application;

import com.ftd.fraud_transaction_detector.aml.training.api.ProductionCandidateTrainingResponse;
import com.ftd.fraud_transaction_detector.aml.training.api.RegisterBatchCandidateRequest;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingRun;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.AmlTrainingRunRepository;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import com.ftd.fraud_transaction_detector.fraud.client.ModelTrainingClient;
import com.ftd.fraud_transaction_detector.fraud.dto.TrainModelRequest;
import com.ftd.fraud_transaction_detector.fraud.dto.TrainModelResponse;
import com.ftd.fraud_transaction_detector.transactions.entity.Transaction;
import com.ftd.fraud_transaction_detector.transactions.repo.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductionCandidateTrainingService {

    private static final String HALF_SPACE_TREES = "HALF_SPACE_TREES";
    private static final String ONLINE_ONE_CLASS_SVM = "ONLINE_ONE_CLASS_SVM";
    // Values are the model names the Python service accepts.
    private static final Map<String, String> BATCH_MODELS = Map.of(
            "ISOLATION_FOREST", "IsolationForest",
            "ONE_CLASS_SVM", "OneClassSVM",
            "AUTOENCODER", "Autoencoder"
    );

    private static final Logger log = LoggerFactory.getLogger(ProductionCandidateTrainingService.class);

    private final AmlTrainingRunRepository runRepository;
    private final IncrementalTrainingService trainingService;
    private final AppConfigService appConfigService;
    private final TransactionRepository transactionRepository;
    private final ModelTrainingClient modelTrainingClient;
    private final BatchCandidateRegistrar batchCandidateRegistrar;

    public ProductionCandidateTrainingService(
            AmlTrainingRunRepository runRepository,
            IncrementalTrainingService trainingService,
            AppConfigService appConfigService,
            TransactionRepository transactionRepository,
            ModelTrainingClient modelTrainingClient,
            BatchCandidateRegistrar batchCandidateRegistrar
    ) {
        this.runRepository = runRepository;
        this.trainingService = trainingService;
        this.appConfigService = appConfigService;
        this.transactionRepository = transactionRepository;
        this.modelTrainingClient = modelTrainingClient;
        this.batchCandidateRegistrar = batchCandidateRegistrar;
    }

    @Transactional
    public ProductionCandidateTrainingResponse start(UUID snapshotRunId, List<String> selectedModels, String requestedBy) {
        AmlTrainingRun snapshot = runRepository.findRequired(snapshotRunId);
        if (!"DATASET_READY".equals(snapshot.status()) || snapshot.datasetPath() == null
                || snapshot.datasetChecksum() == null) {
            throw new IllegalStateException("Production candidate training requires a DATASET_READY snapshot");
        }

        List<String> enabledModels = enabledModels();

        List<String> batchModels = enabledModels.stream()
                .filter(BATCH_MODELS::containsKey)
                .toList();
        String batchStatus = null;
        String batchMessage = null;
        if (!batchModels.isEmpty()) {
            TrainModelResponse batchResponse = trainBatchModels(snapshot, batchModels, requestedBy);
            batchStatus = batchResponse.status();
            batchMessage = batchResponse.message();
            if (!"SUCCESS".equalsIgnoreCase(batchStatus)) {
                throw new IllegalStateException(batchMessage == null || batchMessage.isBlank()
                        ? "Batch model training failed"
                        : batchMessage);
            }
            registerBatchCandidates(snapshot, batchModels, batchResponse, requestedBy);
        }

        List<AmlTrainingRun> prepared = new ArrayList<>();
        List<String> incrementalModels = enabledModels.stream()
                .filter(modelType -> HALF_SPACE_TREES.equals(modelType) || ONLINE_ONE_CLASS_SVM.equals(modelType))
                .toList();
        for (String modelType : incrementalModels) {
            prepared.add(modelType.equals(snapshot.modelType())
                    ? snapshot
                    : runRepository.createReadySibling(snapshot, modelType));
        }
        List<AmlTrainingRun> trainingRuns = prepared.stream()
                .map(run -> trainingService.start(run.trainingRunId(), null))
                .toList();
        return new ProductionCandidateTrainingResponse(
                trainingRuns,
                incrementalModels,
                batchModels,
                batchStatus,
                batchMessage
        );
    }

    private TrainModelResponse trainBatchModels(AmlTrainingRun snapshot, List<String> batchModels, String requestedBy) {
        List<Transaction> transactions = transactionRepository.findEligibleForTraining(
                snapshot.fromBusinessDate(),
                snapshot.toBusinessDate(),
                snapshot.cutoffTimestamp()
        );
        if (transactions.isEmpty()) {
            throw new IllegalStateException("No transactions are available inside the selected training window");
        }
        List<TrainModelRequest.TrainingTransaction> payload = transactions.stream()
                .map(this::toTrainingTransaction)
                .toList();
        return modelTrainingClient.train(new TrainModelRequest(
                "AML_TRAINING_RUN_" + snapshot.trainingRunId(),
                normalizeActor(requestedBy),
                payload,
                appConfigService.getMlHyperparams(),
                batchModels.stream().map(BATCH_MODELS::get).toList(),
                null,
                null
        ));
    }

    /**
     * Gives every batch model its own run and registry entry, so the model registry — and the
     * comparison page built on it — reflects all trained models rather than only the
     * incremental ones. Each entry points at the shared artifact bundle that /train produced,
     * which is the same bundle live scoring loads.
     *
     * A failure here must not fail the pipeline: the models are trained and serving
     * regardless, so registration problems are logged and skipped.
     */
    private void registerBatchCandidates(
            AmlTrainingRun snapshot,
            List<String> batchModels,
            TrainModelResponse response,
            String requestedBy
    ) {
        String artifactPath = response.artifactBasePath();
        if (artifactPath == null || artifactPath.isBlank()) {
            log.warn("Batch training returned no artifact path; skipping registry entries");
            return;
        }
        for (String modelType : batchModels) {
            String pythonName = BATCH_MODELS.get(modelType);
            Map<String, Object> metrics = response.metrics() == null
                    ? Map.of()
                    : response.metrics().getOrDefault(pythonName, Map.of());
            try {
                batchCandidateRegistrar.register(snapshot, modelType, new RegisterBatchCandidateRequest(
                        batchModelVersion(snapshot, modelType),
                        artifactPath,
                        learnedRowCount(response, metrics),
                        doubleValue(metrics, "anomalyRate"),
                        longValue(metrics, "evaluationRows"),
                        longValue(metrics, "anomalyCount"),
                        doubleValue(metrics, "averageScore"),
                        doubleValue(metrics, "scoreP95"),
                        doubleValue(metrics, "scoreP99"),
                        Map.of("pythonModelName", pythonName),
                        metrics,
                        normalizeActor(requestedBy)
                ));
            } catch (Exception exception) {
                log.error("Could not register batch candidate {}: {}", modelType, exception.getMessage());
            }
        }
    }

    private long learnedRowCount(TrainModelResponse response, Map<String, Object> metrics) {
        if (response.trainedRows() > 0) return response.trainedRows();
        Long evaluated = longValue(metrics, "evaluationRows");
        return evaluated == null ? 0L : evaluated;
    }

    private String batchModelVersion(AmlTrainingRun snapshot, String modelType) {
        String segment = snapshot.modelSegment() == null ? "GLOBAL" : snapshot.modelSegment();
        segment = segment.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "-");
        String prefix = switch (modelType) {
            case "ISOLATION_FOREST" -> "IF";
            case "ONE_CLASS_SVM" -> "OCSVM-BATCH";
            case "AUTOENCODER" -> "AE";
            default -> modelType.replaceAll("[^A-Z0-9]+", "-");
        };
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return prefix + "-" + segment + "-" + date + "-" + snapshot.trainingRunId().toString().substring(0, 8);
    }

    private Double doubleValue(Map<String, Object> metrics, String key) {
        Object value = metrics.get(key);
        return value instanceof Number number && Double.isFinite(number.doubleValue())
                ? number.doubleValue()
                : null;
    }

    private Long longValue(Map<String, Object> metrics, String key) {
        Object value = metrics.get(key);
        return value instanceof Number number ? number.longValue() : null;
    }

    /**
     * Every model whose "Training enabled" switch is on in Model Tuning.
     *
     * Training deliberately ignores the Risk Policy — that governs which trained models
     * contribute to a scoring decision, which is a separate question from which models are
     * worth building at all.
     */
    private List<String> enabledModels() {
        List<String> all = new ArrayList<>(BATCH_MODELS.keySet());
        all.add(HALF_SPACE_TREES);
        all.add(ONLINE_ONE_CLASS_SVM);
        List<String> enabled = all.stream()
                .filter(modelType -> appConfigService.isModelTrainingEnabled(modelType, true))
                .toList();
        if (enabled.isEmpty()) {
            throw new IllegalStateException(
                    "Every model is disabled in Model Tuning; enable at least one before training");
        }
        if (enabled.size() < all.size()) {
            List<String> skipped = all.stream().filter(model -> !enabled.contains(model)).toList();
            log.info("Skipping models disabled in Model Tuning: {}", skipped);
        }
        return enabled;
    }

    private TrainModelRequest.TrainingTransaction toTrainingTransaction(Transaction transaction) {
        return new TrainModelRequest.TrainingTransaction(
                transaction.getTransactionId(),
                transaction.getAccountId(),
                transaction.getTransactionAmount(),
                transaction.getTransactionType(),
                transaction.getTransactionDate(),
                transaction.getLocation(),
                transaction.getChannel(),
                transaction.getCustomerAge(),
                transaction.getCustomerOccupation(),
                transaction.getLoginAttempts(),
                transaction.getAccountBalance()
        );
    }

    private String normalizeActor(String requestedBy) {
        return requestedBy == null || requestedBy.isBlank() ? "training-operations-ui" : requestedBy.trim();
    }
}
