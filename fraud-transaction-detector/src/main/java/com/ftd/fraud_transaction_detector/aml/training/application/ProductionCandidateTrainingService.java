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

    private static final Map<String, String> UNSUPERVISED_MODELS = Map.of(
            "ISOLATION_FOREST", "IsolationForest",
            "AUTOENCODER", "Autoencoder",
            "LOCAL_OUTLIER_FACTOR", "LOF"
    );
    private static final Map<String, String> SUPERVISED_MODELS = Map.of(
            "XGBOOST_CLASSIFIER", "XGBoost",
            "RANDOM_FOREST_CLASSIFIER", "RandomForestClassifier",
            "LOGISTIC_REGRESSION", "LogisticRegression"
    );

    private static final Logger log = LoggerFactory.getLogger(ProductionCandidateTrainingService.class);

    private final AmlTrainingRunRepository runRepository;
    private final AppConfigService appConfigService;
    private final TransactionRepository transactionRepository;
    private final ModelTrainingClient modelTrainingClient;
    private final BatchCandidateRegistrar batchCandidateRegistrar;

    public ProductionCandidateTrainingService(
            AmlTrainingRunRepository runRepository,
            AppConfigService appConfigService,
            TransactionRepository transactionRepository,
            ModelTrainingClient modelTrainingClient,
            BatchCandidateRegistrar batchCandidateRegistrar
    ) {
        this.runRepository = runRepository;
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

        List<String> enabledModels = enabledModels(selectedModels);

        TrainModelResponse response = trainModels(snapshot, enabledModels, requestedBy);
        if (!"SUCCESS".equalsIgnoreCase(response.status())) {
            throw new IllegalStateException(response.message() == null || response.message().isBlank()
                    ? "Model training failed"
                    : response.message());
        }
        registerCandidates(snapshot, enabledModels, response, requestedBy);
        return new ProductionCandidateTrainingResponse(
                enabledModels,
                response.status(),
                response.message()
        );
    }

    private TrainModelResponse trainModels(AmlTrainingRun snapshot, List<String> models, String requestedBy) {
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
        String learningMode = appConfigService.getLearningMode();
        Map<String, String> productionModels = productionModels();
        return modelTrainingClient.train(new TrainModelRequest(
                "AML_TRAINING_RUN_" + snapshot.trainingRunId(),
                normalizeActor(requestedBy),
                payload,
                appConfigService.getMlHyperparams(),
                models.stream().map(productionModels::get).toList(),
                null,
                null,
                learningMode
        ));
    }

    /**
     * Gives every selected model its own registry entry while all three artifacts remain in
     * the shared bundle produced by the unified training request.
     *
     * A failure here must not fail the pipeline: the models are trained and serving
     * regardless, so registration problems are logged and skipped.
     */
    private void registerCandidates(
            AmlTrainingRun snapshot,
            List<String> models,
            TrainModelResponse response,
            String requestedBy
    ) {
        String artifactPath = response.artifactBasePath();
        if (artifactPath == null || artifactPath.isBlank()) {
            log.warn("Training returned no artifact path; skipping registry entries");
            return;
        }
        for (String modelType : models) {
            String pythonName = productionModels().get(modelType);
            Map<String, Object> metrics = response.metrics() == null
                    ? Map.of()
                    : response.metrics().getOrDefault(pythonName, Map.of());
            try {
                batchCandidateRegistrar.register(snapshot, modelType, new RegisterBatchCandidateRequest(
                        modelVersion(snapshot, modelType),
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
                log.error("Could not register candidate {}: {}", modelType, exception.getMessage());
            }
        }
    }

    private long learnedRowCount(TrainModelResponse response, Map<String, Object> metrics) {
        if (response.trainedRows() > 0) return response.trainedRows();
        Long evaluated = longValue(metrics, "evaluationRows");
        return evaluated == null ? 0L : evaluated;
    }

    private String modelVersion(AmlTrainingRun snapshot, String modelType) {
        String segment = snapshot.modelSegment() == null ? "GLOBAL" : snapshot.modelSegment();
        segment = segment.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "-");
        String prefix = switch (modelType) {
            case "ISOLATION_FOREST" -> "IF";
            case "AUTOENCODER" -> "AE";
            case "LOCAL_OUTLIER_FACTOR" -> "LOF";
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
    private List<String> enabledModels(List<String> selectedModels) {
        List<String> all = new ArrayList<>(productionModels().keySet());
        List<String> trainable = all.stream()
                .filter(modelType -> appConfigService.isModelTrainingEnabled(modelType, true))
                .toList();
        if (trainable.isEmpty()) {
            throw new IllegalStateException(
                    "Every model is disabled in Model Tuning; enable at least one before training");
        }
        if (selectedModels == null || selectedModels.isEmpty()) {
            return trainable;
        }
        List<String> requested = selectedModels.stream()
                .filter(java.util.Objects::nonNull)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        List<String> unsupported = requested.stream().filter(model -> !all.contains(model)).toList();
        if (!unsupported.isEmpty()) {
            throw new IllegalArgumentException("Unsupported production training models: " + unsupported);
        }
        List<String> disabled = requested.stream().filter(model -> !trainable.contains(model)).toList();
        if (!disabled.isEmpty()) {
            throw new IllegalStateException("Selected models are disabled in Model Tuning: " + disabled);
        }
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("Select at least one model to train");
        }
        if (requested.size() < all.size()) {
            List<String> skipped = all.stream().filter(model -> !requested.contains(model)).toList();
            log.info("Skipping models not selected or disabled for this run: {}", skipped);
        }
        return requested;
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
                transaction.getAccountBalance(),
                transaction.getFraudLabel()
        );
    }

    private Map<String, String> productionModels() {
        return appConfigService.isSupervisedLearningMode() ? SUPERVISED_MODELS : UNSUPERVISED_MODELS;
    }

    private String normalizeActor(String requestedBy) {
        return requestedBy == null || requestedBy.isBlank() ? "training-operations-ui" : requestedBy.trim();
    }
}
