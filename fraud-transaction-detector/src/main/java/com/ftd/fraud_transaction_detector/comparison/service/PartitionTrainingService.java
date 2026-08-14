package com.ftd.fraud_transaction_detector.comparison.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.comparison.dto.ModelVersionResponse;
import com.ftd.fraud_transaction_detector.comparison.dto.PartitionTrainingRequest;
import com.ftd.fraud_transaction_detector.comparison.entity.DatasetPartition;
import com.ftd.fraud_transaction_detector.comparison.entity.ModelVersion;
import com.ftd.fraud_transaction_detector.comparison.entity.UploadedDataset;
import com.ftd.fraud_transaction_detector.comparison.repo.DatasetPartitionRepository;
import com.ftd.fraud_transaction_detector.comparison.repo.ModelVersionRepository;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import com.ftd.fraud_transaction_detector.fraud.client.ModelTrainingClient;
import com.ftd.fraud_transaction_detector.fraud.dto.TrainModelRequest;
import com.ftd.fraud_transaction_detector.fraud.dto.TrainModelResponse;
import com.ftd.fraud_transaction_detector.fraud.entity.TrainingRun;
import com.ftd.fraud_transaction_detector.fraud.repo.TrainingRunRepository;
import com.ftd.fraud_transaction_detector.transactions.entity.Transaction;
import com.ftd.fraud_transaction_detector.transactions.repo.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PartitionTrainingService {

    private static final int MINIMUM_DATASET_ROWS = 200;

    private static final List<String> DEFAULT_COMPARISON_MODELS = List.of(
            "IsolationForest",
            "Autoencoder",
            "LOF"
    );

    private final DatasetPartitionRepository datasetPartitionRepository;
    private final TransactionRepository transactionRepository;
    private final AppConfigService appConfigService;
    private final ModelTrainingClient modelTrainingClient;
    private final TrainingRunRepository trainingRunRepository;
    private final ModelVersionRepository modelVersionRepository;
    private final ObjectMapper objectMapper;

    public PartitionTrainingService(
            DatasetPartitionRepository datasetPartitionRepository,
            TransactionRepository transactionRepository,
            AppConfigService appConfigService,
            ModelTrainingClient modelTrainingClient,
            TrainingRunRepository trainingRunRepository,
            ModelVersionRepository modelVersionRepository,
            ObjectMapper objectMapper
    ) {
        this.datasetPartitionRepository = datasetPartitionRepository;
        this.transactionRepository = transactionRepository;
        this.appConfigService = appConfigService;
        this.modelTrainingClient = modelTrainingClient;
        this.trainingRunRepository = trainingRunRepository;
        this.modelVersionRepository = modelVersionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<ModelVersionResponse> trainPartition(Long partitionId, PartitionTrainingRequest request) {
        DatasetPartition partition = datasetPartitionRepository.findById(partitionId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset partition not found: " + partitionId));

        UploadedDataset dataset = partition.getUploadedDataset();
        if (dataset.getTotalRows() == null || dataset.getTotalRows() < MINIMUM_DATASET_ROWS) {
            throw new IllegalArgumentException(
                    "At least " + MINIMUM_DATASET_ROWS + " valid dataset rows are required for training"
            );
        }
        int holdoutRows = DatasetPartitionService.commonHoldoutRows(dataset.getTotalRows());
        int trainingPoolRows = dataset.getTotalRows() - holdoutRows;
        int requestedTrainingRows = Math.min(partition.getPartitionSize(), trainingPoolRows);
        PageRequest pageRequest = PageRequest.of(
                0,
                requestedTrainingRows,
                Sort.by(Sort.Direction.ASC, "transactionDate", "id")
        );
        List<Transaction> transactions = dataset.getSnapshotMaxTransactionId() == null
                ? transactionRepository.findByUploadBatchId(dataset.getSourceBatchId(), pageRequest).getContent()
                : transactionRepository.findByIdLessThanEqual(dataset.getSnapshotMaxTransactionId(), pageRequest).getContent();
        PageRequest evaluationPage = PageRequest.of(
                0,
                holdoutRows,
                Sort.by(Sort.Direction.DESC, "transactionDate", "id")
        );
        List<Transaction> evaluationTransactions = dataset.getSnapshotMaxTransactionId() == null
                ? transactionRepository.findByUploadBatchId(dataset.getSourceBatchId(), evaluationPage).getContent()
                : transactionRepository.findByIdLessThanEqual(dataset.getSnapshotMaxTransactionId(), evaluationPage).getContent();

        if (transactions.isEmpty()) {
            throw new IllegalArgumentException("No transactions found for dataset partition: " + partitionId);
        }
        if (evaluationTransactions.isEmpty()) {
            throw new IllegalArgumentException("No common future holdout rows found for dataset partition: " + partitionId);
        }

        String requestedBy = request == null || request.requestedBy() == null || request.requestedBy().isBlank()
                ? "comparison-ui"
                : request.requestedBy().trim();

        List<String> selectedModels = normalizeModelNames(request == null ? null : request.modelNames());
        Map<String, Object> hyperparams = new LinkedHashMap<>(appConfigService.getMlHyperparams());
        hyperparams.putIfAbsent("ml.optimization.enabled", true);
        hyperparams.putIfAbsent("ml.optimization.validation_fraction", 0.20);
        hyperparams.putIfAbsent("ml.optimization.target_anomaly_rate", 0.05);
        hyperparams.putIfAbsent("ml.optimization.min_rows", 200);
        hyperparams.putIfAbsent("ml.optimization.max_training_rows", 5000);

        TrainingRun run = startRun(partition, requestedBy, transactions.size(), selectedModels, hyperparams);

        String outputSubdir = "versions/" + run.getRunNo();
        TrainModelRequest payload = new TrainModelRequest(
                "PARTITION_" + partition.getPartitionSize(),
                requestedBy,
                transactions.stream().map(PartitionTrainingService::toTrainingTxn).toList(),
                hyperparams,
                selectedModels,
                outputSubdir,
                evaluationTransactions.stream().map(PartitionTrainingService::toTrainingTxn).toList()
        );

        try {
            TrainModelResponse response = modelTrainingClient.train(payload);
            completeRun(run, response);
            if (!"SUCCESS".equalsIgnoreCase(response.status())) {
                throw new IllegalStateException(response.message());
            }
            return saveModelVersions(partition, run, response, selectedModels);
        } catch (RuntimeException ex) {
            failRun(run, ex.getMessage());
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<ModelVersionResponse> listModelVersions(Long partitionId) {
        return modelVersionRepository.findByDatasetPartitionIdOrderByCreatedAtDescIdDesc(partitionId)
                .stream()
                .map(ModelVersionResponse::from)
                .toList();
    }

    private TrainingRun startRun(
            DatasetPartition partition,
            String requestedBy,
            int trainingRowCount,
            List<String> selectedModels,
            Map<String, Object> hyperparams
    ) {
        Instant now = Instant.now();
        TrainingRun run = new TrainingRun();
        run.setRunNo("TRAIN-" + now.toEpochMilli());
        run.setSource("DATASET_PARTITION:" + partition.getId());
        run.setRequestedBy(requestedBy);
        run.setStatus("RUNNING");
        run.setTrainingRowCount(trainingRowCount);
        run.setHyperparamsJson(toJsonSafe(hyperparams));
        run.setModelsJson(toJsonSafe(selectedModels));
        run.setMessage("Training partition " + partition.getPartitionLabel());
        run.setStartedAt(now);
        return trainingRunRepository.save(run);
    }

    private void completeRun(TrainingRun run, TrainModelResponse response) {
        Instant completedAt = Instant.now();
        run.setStatus("SUCCESS".equalsIgnoreCase(response.status()) ? "SUCCESS" : "FAILED");
        run.setResponseStatus(response.status());
        run.setMessage(response.message());
        run.setTrainingRowCount(response.trainedRows());
        run.setFeatureCount(response.featureCount());
        run.setModelsJson(toJsonSafe(response.models()));
        run.setArtifactsJson(toJsonSafe(response.artifacts()));
        run.setCompletedAt(completedAt);
        run.setDurationMs(Duration.between(run.getStartedAt(), completedAt).toMillis());
        trainingRunRepository.save(run);
    }

    private void failRun(TrainingRun run, String message) {
        Instant completedAt = Instant.now();
        run.setStatus("FAILED");
        run.setResponseStatus("FAILED");
        run.setMessage(message == null || message.isBlank() ? "Training failed" : message);
        run.setCompletedAt(completedAt);
        run.setDurationMs(Duration.between(run.getStartedAt(), completedAt).toMillis());
        trainingRunRepository.save(run);
    }

    private List<ModelVersionResponse> saveModelVersions(
            DatasetPartition partition,
            TrainingRun run,
            TrainModelResponse response,
            List<String> selectedModels
    ) {
        Map<String, String> artifacts = response.artifacts() == null ? Map.of() : response.artifacts();
        Map<String, Map<String, Object>> metrics = response.metrics() == null ? Map.of() : response.metrics();
        String artifactBasePath = response.artifactBasePath() == null ? "" : response.artifactBasePath();

        return selectedModels.stream()
                .map(modelName -> {
                    String artifactKey = artifactKeyForModel(modelName);
                    String modelPath = artifacts.get(artifactKey);
                    if (modelPath == null || modelPath.isBlank()) {
                        throw new IllegalStateException("Training response missing artifact path for model: " + modelName);
                    }
                    ModelVersion modelVersion = new ModelVersion();
                    modelVersion.setModelVersionNo("MV-" + Instant.now().toEpochMilli() + "-" + sanitizeModelName(modelName));
                    modelVersion.setTrainingRun(run);
                    modelVersion.setDatasetPartition(partition);
                    modelVersion.setModelName(modelName);
                    modelVersion.setPartitionSize(response.trainedRows());
                    modelVersion.setArtifactBasePath(artifactBasePath);
                    modelVersion.setFeatureColumnsPath(artifacts.get("featureColumns"));
                    modelVersion.setScalerPath(artifacts.get("scaler"));
                    modelVersion.setModelPath(modelPath);
                    modelVersion.setMetricsJson(toJsonSafe(metrics.get(modelName)));
                    modelVersion.setIsActive(Boolean.FALSE);
                    modelVersion.setLifecycleStatus("CANDIDATE");
                    modelVersion.setCreatedAt(Instant.now());
                    return ModelVersionResponse.from(modelVersionRepository.save(modelVersion));
                })
                .toList();
    }

    private static TrainModelRequest.TrainingTransaction toTrainingTxn(Transaction t) {
        return new TrainModelRequest.TrainingTransaction(
                t.getTransactionId(),
                t.getAccountId(),
                t.getTransactionAmount(),
                t.getTransactionType(),
                t.getTransactionDate(),
                t.getLocation(),
                t.getChannel(),
                t.getCustomerAge(),
                t.getCustomerOccupation(),
                t.getLoginAttempts(),
                t.getAccountBalance()
        );
    }

    private static List<String> normalizeModelNames(List<String> requestedModels) {
        if (requestedModels == null || requestedModels.isEmpty()) {
            return DEFAULT_COMPARISON_MODELS;
        }
        return requestedModels.stream()
                .map(modelName -> modelName == null ? "" : modelName.trim())
                .filter(modelName -> !modelName.isBlank())
                .distinct()
                .toList();
    }

    private static String artifactKeyForModel(String modelName) {
        return switch (modelName) {
            case "IsolationForest" -> "isolationForest";
            case "LOF" -> "localOutlierFactor";
            case "OneClassSVM" -> "oneClassSvm";
            case "EllipticEnvelope" -> "ellipticEnvelope";
            case "PCAReconstruction" -> "pcaReconstruction";
            default -> throw new IllegalArgumentException("Unsupported model name: " + modelName);
        };
    }

    private static String sanitizeModelName(String modelName) {
        return modelName.replaceAll("[^A-Za-z0-9]+", "").toUpperCase();
    }

    private String toJsonSafe(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{\"serializationError\":\"" + ex.getMessage().replace("\"", "'") + "\"}";
        }
    }
}
