package com.ftd.fraud_transaction_detector.fraud.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.fraud.client.ModelTrainingClient;
import com.ftd.fraud_transaction_detector.fraud.dto.TrainModelRequest;
import com.ftd.fraud_transaction_detector.fraud.dto.TrainModelResponse;
import com.ftd.fraud_transaction_detector.fraud.dto.TrainingRunResponse;
import com.ftd.fraud_transaction_detector.fraud.entity.TrainingRun;
import com.ftd.fraud_transaction_detector.fraud.repo.TrainingRunRepository;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import com.ftd.fraud_transaction_detector.transactions.entity.Transaction;
import com.ftd.fraud_transaction_detector.transactions.repo.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ModelTrainingService {

    private final TransactionRepository transactionRepository;
    private final ModelTrainingClient modelTrainingClient;
    private final AppConfigService appConfigService;
    private final TrainingRunRepository trainingRunRepository;
    private final ObjectMapper objectMapper;

    public ModelTrainingService(
            TransactionRepository transactionRepository,
            ModelTrainingClient modelTrainingClient,
            AppConfigService appConfigService,
            TrainingRunRepository trainingRunRepository,
            ObjectMapper objectMapper
    ) {
        this.transactionRepository = transactionRepository;
        this.modelTrainingClient = modelTrainingClient;
        this.appConfigService = appConfigService;
        this.trainingRunRepository = trainingRunRepository;
        this.objectMapper = objectMapper;
    }

    public TrainModelResponse trainFromDatabase(String requestedBy) {
        String normalizedRequestedBy = requestedBy == null || requestedBy.isBlank() ? "spring-boot" : requestedBy;
        List<Transaction> txns = transactionRepository.findAll();
        if (txns.isEmpty()) {
            throw new IllegalArgumentException("No transactions found in database to train models");
        }

        List<TrainModelRequest.TrainingTransaction> payloadTxns = txns.stream()
                .map(ModelTrainingService::toTrainingTxn)
                .toList();

        Map<String, Object> hyperparams = appConfigService.getMlHyperparams();
        TrainingRun run = startRun("SPRING_BOOT_DB", normalizedRequestedBy, payloadTxns.size(), hyperparams);

        TrainModelRequest req = new TrainModelRequest(
                "SPRING_BOOT_DB",
                normalizedRequestedBy,
                payloadTxns,
                hyperparams,
                null,
                null,
                null
        );
        try {
            TrainModelResponse response = modelTrainingClient.train(req);
            completeRun(run, response);
            return response;
        } catch (RuntimeException ex) {
            failRun(run, ex.getMessage());
            throw ex;
        }
    }

    public Page<TrainingRunResponse> listTrainingRuns(Pageable pageable) {
        return trainingRunRepository.findAll(pageable).map(TrainingRunResponse::from);
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

    private TrainingRun startRun(
            String source,
            String requestedBy,
            int trainingRowCount,
            Map<String, Object> hyperparams
    ) {
        Instant now = Instant.now();
        TrainingRun run = new TrainingRun();
        run.setRunNo("TRAIN-" + now.toEpochMilli());
        run.setSource(source);
        run.setRequestedBy(requestedBy);
        run.setStatus("RUNNING");
        run.setTrainingRowCount(trainingRowCount);
        run.setHyperparamsJson(toJsonSafe(hyperparams));
        run.setStartedAt(now);
        return trainingRunRepository.save(run);
    }

    private void completeRun(TrainingRun run, TrainModelResponse response) {
        Instant completedAt = Instant.now();
        boolean success = response != null && "SUCCESS".equalsIgnoreCase(response.status());
        run.setStatus(success ? "SUCCESS" : "FAILED");
        if (response != null) {
            run.setResponseStatus(response.status());
            run.setMessage(response.message());
            run.setTrainingRowCount(response.trainedRows());
            run.setFeatureCount(response.featureCount());
            run.setModelsJson(toJsonSafe(response.models()));
            run.setArtifactsJson(toJsonSafe(response.artifacts()));
        }
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

    private String toJsonSafe(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{\"serializationError\":\"" + ex.getMessage().replace("\"", "'") + "\"}";
        }
    }
}
