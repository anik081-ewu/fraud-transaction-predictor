package com.ftd.fraud_transaction_detector.transactions.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.casework.AutomaticCaseRequested;
import com.ftd.fraud_transaction_detector.aml.feature.application.FeatureContextLoader;
import com.ftd.fraud_transaction_detector.aml.feature.application.FeatureEngineeringService;
import com.ftd.fraud_transaction_detector.aml.feature.application.FeatureVersionProvider;
import com.ftd.fraud_transaction_detector.aml.feature.domain.FeatureContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.HistoricalTransaction;
import com.ftd.fraud_transaction_detector.aml.feature.infrastructure.FeaturePersistenceService;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TransactionFeatureVector;
import com.ftd.fraud_transaction_detector.aml.profile.application.CustomerProfileService;
import com.ftd.fraud_transaction_detector.aml.learning.application.LearningEligibilityService;
import com.ftd.fraud_transaction_detector.aml.learning.domain.LearningEligibilityDecision;
import com.ftd.fraud_transaction_detector.aml.prediction.AmlPredictionOrchestrator;
import com.ftd.fraud_transaction_detector.fraud.dto.FraudPredictionRequest;
import com.ftd.fraud_transaction_detector.fraud.dto.FraudPredictionResponse;
import com.ftd.fraud_transaction_detector.fraud.entity.FraudAlert;
import com.ftd.fraud_transaction_detector.fraud.entity.FraudPredictionLog;
import com.ftd.fraud_transaction_detector.fraud.repo.FraudAlertRepository;
import com.ftd.fraud_transaction_detector.fraud.repo.FraudPredictionLogRepository;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import com.ftd.fraud_transaction_detector.transactions.entity.Transaction;
import com.ftd.fraud_transaction_detector.transactions.repo.TransactionRepository;
import com.ftd.fraud_transaction_detector.transactions.web.dto.CreateTransactionRequest;
import com.ftd.fraud_transaction_detector.transactions.web.dto.CreateTransactionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TransactionCreateService {

    private final TransactionRepository transactionRepository;
    private final AmlPredictionOrchestrator amlPredictionOrchestrator;
    private final FraudPredictionLogRepository fraudPredictionLogRepository;
    private final FraudAlertRepository fraudAlertRepository;
    private final ObjectMapper objectMapper;
    private final AppConfigService appConfigService;
    private final ApplicationEventPublisher eventPublisher;
    private final FeatureContextLoader featureContextLoader;
    private final FeatureEngineeringService featureEngineeringService;
    private final FeatureVersionProvider featureVersionProvider;
    private final FeaturePersistenceService featurePersistenceService;
    private final CustomerProfileService customerProfileService;
    private final LearningEligibilityService learningEligibilityService;

    public TransactionCreateService(
            TransactionRepository transactionRepository,
            AmlPredictionOrchestrator amlPredictionOrchestrator,
            FraudPredictionLogRepository fraudPredictionLogRepository,
            FraudAlertRepository fraudAlertRepository,
            ObjectMapper objectMapper,
            AppConfigService appConfigService,
            ApplicationEventPublisher eventPublisher,
            FeatureContextLoader featureContextLoader,
            FeatureEngineeringService featureEngineeringService,
            FeatureVersionProvider featureVersionProvider,
            FeaturePersistenceService featurePersistenceService,
            CustomerProfileService customerProfileService,
            LearningEligibilityService learningEligibilityService
    ) {
        this.transactionRepository = transactionRepository;
        this.amlPredictionOrchestrator = amlPredictionOrchestrator;
        this.fraudPredictionLogRepository = fraudPredictionLogRepository;
        this.fraudAlertRepository = fraudAlertRepository;
        this.objectMapper = objectMapper;
        this.appConfigService = appConfigService;
        this.eventPublisher = eventPublisher;
        this.featureContextLoader = featureContextLoader;
        this.featureEngineeringService = featureEngineeringService;
        this.featureVersionProvider = featureVersionProvider;
        this.featurePersistenceService = featurePersistenceService;
        this.customerProfileService = customerProfileService;
        this.learningEligibilityService = learningEligibilityService;
    }

    @Transactional
    public CreateTransactionResponse create(CreateTransactionRequest req) {
        if (transactionRepository.existsByTransactionId(req.transactionId())) {
            throw new IllegalArgumentException("transactionId already exists: " + req.transactionId());
        }

        Transaction txn = new Transaction();
        txn.setTransactionId(req.transactionId());
        txn.setAccountId(req.accountId());
        txn.setCustomerId(req.accountId());
        txn.setBusinessDate(req.transactionDate().toLocalDate());
        txn.setTransactionAmount(req.transactionAmount());
        txn.setTransactionType(req.transactionType());
        txn.setTransactionDate(req.transactionDate());
        txn.setLocation(req.location());
        txn.setChannel(req.channel());
        txn.setCustomerAge(req.customerAge());
        txn.setCustomerOccupation(req.customerOccupation());
        txn.setLoginAttempts(req.loginAttempts() == null ? 0 : req.loginAttempts());
        txn.setAccountBalance(req.accountBalance() == null ? BigDecimal.ZERO : req.accountBalance());
        txn.setSourceType("API");
        txn.setUploadBatchId(null);
        Instant now = Instant.now();
        txn.setCreatedAt(now);
        txn.setUpdatedAt(now);
        txn.setProcessingStatus("PROCESSING");
        txn.setFeatureStatus("PENDING");
        txn.setPredictionStatus("NOT_STARTED");
        transactionRepository.saveAndFlush(txn);

        var featureContext = featureContextLoader.load(txn);
        var featureVector = featureEngineeringService.calculate(
                featureContext,
                featureVersionProvider.currentVersion(),
                appConfigService.getStructuringReportingThreshold(BigDecimal.valueOf(10_000))
        );
        featurePersistenceService.save(featureVector);
        txn.setFeatureStatus("COMPLETED");
        txn.setPredictionStatus("PROCESSING");
        txn.setUpdatedAt(Instant.now());

        FraudPredictionRequest predictionRequest = buildPredictionRequest(txn, featureContext, featureVector);
        FraudPredictionResponse predictionResponse = predictWithMinHistoryGate(predictionRequest, featureVector);
        LearningEligibilityDecision learningDecision = learningEligibilityService.evaluateAndPersist(
                txn, predictionResponse
        );

        savePredictionLog(predictionRequest, predictionResponse, featureVector, learningDecision);
        txn.setPredictionStatus("COMPLETED");
        txn.setProcessingStatus("COMPLETED");
        Instant completedAt = Instant.now();
        txn.setUpdatedAt(completedAt);
        txn.setFraudLabel(false);
        txn.setLabelSource("AUTO_NO_CASE");
        txn.setLabeledBy("anomaly-engine");
        txn.setLabeledAt(completedAt);

        if (predictionResponse.suspicious()) {
            FraudAlert alert = fraudAlertRepository.save(buildAlert(predictionResponse));
            eventPublisher.publishEvent(new AutomaticCaseRequested(
                    alert.getId(),
                    alert.getTransactionId(),
                    alert.getAccountId(),
                    alert.getRiskLevel(),
                    alert.getAnomalyVotes()
            ));
        }
        customerProfileService.applyPredictionOutcome(txn, predictionResponse, learningDecision);

        return new CreateTransactionResponse(
                predictionResponse.transactionId(),
                predictionResponse.accountId(),
                predictionResponse.riskLevel(),
                predictionResponse.suspicious(),
                predictionResponse.anomalyVotes(),
                predictionResponse.modelResults(),
                predictionResponse.featureSummary(),
                predictionResponse.reasons(),
                predictionResponse.recommendedAction()
        );
    }

    private FraudPredictionRequest buildPredictionRequest(
            Transaction txn,
            FeatureContext featureContext,
            TransactionFeatureVector featureVector
    ) {
        HistoricalTransaction prev = featureContext.recentTransactions().stream()
                .max(Comparator.comparing(HistoricalTransaction::transactionDate))
                .orElse(null);
        long historyCount = featureContext.customerHistoryCount();
        BigDecimal avgAmount = firstBigDecimal(
                featureContext.trustedProfile().averageAmount(),
                featureVector.amount().last30Average()
        );
        BigDecimal maxAmount = firstBigDecimal(
                featureContext.trustedProfile().maximumAmount(),
                featureVector.amount().last30Maximum()
        );
        BigDecimal stdAmount = firstBigDecimal(
                featureContext.trustedProfile().standardDeviationAmount(),
                featureVector.amount().last30StandardDeviation()
        );
        BigDecimal rolling7 = rollingAverage(featureContext.recentTransactions(), txn.getTransactionDate(), 7);
        BigDecimal rolling30 = firstBigDecimal(
                rollingAverage(featureContext.recentTransactions(), txn.getTransactionDate(), 30),
                featureVector.amount().last30Average()
        );
        FraudPredictionRequest.AccountProfileDto accountProfile = new FraudPredictionRequest.AccountProfileDto(
                prev == null ? null : prev.transactionDate(),
                prev == null ? null : prev.location(),
                avgAmount,
                maxAmount,
                stdAmount,
                historyCount,
                rolling7,
                rolling30
        );

        FraudPredictionRequest.TransactionDto transactionDto = new FraudPredictionRequest.TransactionDto(
                txn.getTransactionId(),
                txn.getAccountId(),
                txn.getTransactionAmount(),
                txn.getTransactionType(),
                txn.getTransactionDate(),
                txn.getLocation(),
                txn.getChannel(),
                txn.getLoginAttempts(),
                txn.getAccountBalance()
        );

        FraudPredictionRequest.CustomerDto customerDto = new FraudPredictionRequest.CustomerDto(
                txn.getCustomerAge(),
                txn.getCustomerOccupation()
        );

        return new FraudPredictionRequest(transactionDto, customerDto, accountProfile);
    }

    private BigDecimal rollingAverage(
            List<HistoricalTransaction> history,
            LocalDateTime transactionDate,
            int days
    ) {
        LocalDateTime windowStart = transactionDate.minusDays(days);
        return history.stream()
                .filter(item -> !item.transactionDate().isBefore(windowStart))
                .map(HistoricalTransaction::amount)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .stream()
                .mapToObj(BigDecimal::valueOf)
                .findFirst()
                .orElse(null);
    }

    private BigDecimal firstBigDecimal(Number... candidates) {
        for (Number candidate : candidates) {
            if (candidate instanceof BigDecimal decimal) {
                return decimal;
            }
            if (candidate != null && Double.isFinite(candidate.doubleValue())) {
                return BigDecimal.valueOf(candidate.doubleValue());
            }
        }
        return null;
    }

    private FraudPredictionResponse predictWithMinHistoryGate(
            FraudPredictionRequest request,
            TransactionFeatureVector featureVector
    ) {
        if (!appConfigService.isColdStartEnabled(true)) {
            return amlPredictionOrchestrator.predict(featureVector);
        }
        long historyCount = request.accountProfile().userTxnCount() == null ? 0 : request.accountProfile().userTxnCount();
        int min = appConfigService.getMinTransactionCountBeforePredict(20);
        if (historyCount < min) {
            String reason = "Insufficient transaction history for ML prediction (min=" + min + ", found=" + historyCount + ")";
            Map<String, Object> featureSummary = Map.of(
                    "minHistoryRequired", min,
                    "userTxnCount", historyCount,
                    "coldStart", true
            );
            FraudPredictionResponse coldStartResponse = new FraudPredictionResponse(
                    request.transaction().transactionId(),
                    request.transaction().accountId(),
                    false,
                    "NORMAL",
                    0,
                    Map.of("skipped", Map.of("reason", reason)),
                    featureSummary,
                    List.of(reason),
                    "ALLOW"
            );
            return coldStartResponse;
        }
        return amlPredictionOrchestrator.predict(featureVector);
    }

    private void savePredictionLog(
            FraudPredictionRequest request,
            FraudPredictionResponse response,
            TransactionFeatureVector featureVector,
            LearningEligibilityDecision learningDecision
    ) {
        FraudPredictionLog log = new FraudPredictionLog();
        log.setTransactionId(response.transactionId());
        log.setAccountId(response.accountId());
        log.setRiskLevel(response.riskLevel());
        log.setAnomalyVotes(response.anomalyVotes());
        log.setCreatedAt(Instant.now());
        log.setFeatureVersion(featureVector.featureVersion());
        log.setSuspiciousFlag(response.suspicious());
        log.setLearningDecision(learningDecision.status().name());
        log.setLearningDecisionReason(learningDecision.reason());
        log.setReasonCodes(toJsonSafe(response.reasons()));
        applyIsolationForestBenchmark(response, log);
        applyLayeredRiskMetadata(response, log);
        log.setRequestJson(toJsonSafe(request));
        log.setResponseJson(toJsonSafe(response));
        fraudPredictionLogRepository.save(log);
    }

    private void applyIsolationForestBenchmark(FraudPredictionResponse response, FraudPredictionLog log) {
        if (response.modelResults() == null) return;
        Object raw = response.modelResults().get("IsolationForest");
        if (!(raw instanceof Map<?, ?> result)) return;
        Object score = result.get("decisionFunction");
        if (score instanceof Number number) log.setBatchModelScore(number.doubleValue());
    }

    private void applyLayeredRiskMetadata(FraudPredictionResponse response, FraudPredictionLog log) {
        if (response.modelResults() == null) return;
        Object raw = response.modelResults().get("LayeredRiskArchitecture");
        if (!(raw instanceof Map<?, ?> result) || !Boolean.TRUE.equals(result.get("productionDecision"))) return;
        Object policyVersion = result.get("riskPolicyVersion");
        Object finalScore = result.get("finalRiskScore");
        if (policyVersion != null) log.setRiskPolicyVersion(policyVersion.toString());
        if (finalScore instanceof Number number) log.setFinalRiskScore(number.doubleValue());
    }

    private FraudAlert buildAlert(FraudPredictionResponse response) {
        FraudAlert alert = new FraudAlert();
        alert.setAlertNo("ALERT-" + UUID.randomUUID());
        alert.setTransactionId(response.transactionId());
        alert.setAccountId(response.accountId());
        alert.setRiskLevel(response.riskLevel());
        alert.setAnomalyVotes(response.anomalyVotes());
        alert.setIsoAnomaly(isModelAnomaly(response, "IsolationForest"));
        alert.setLofAnomaly(isModelAnomaly(response, "LOF"));
        alert.setSvmAnomaly(isModelAnomaly(response, "OneClassSVM"));
        alert.setAnomalyReason(joinReasons(response.reasons()));
        alert.setRecommendedAction(response.recommendedAction());
        alert.setReviewStatus("PENDING");
        alert.setCreatedAt(Instant.now());
        return alert;
    }

    private static boolean isModelAnomaly(FraudPredictionResponse response, String modelName) {
        Object modelResult = response.modelResults().get(modelName);
        if (!(modelResult instanceof Map<?, ?> values)) {
            return false;
        }
        return Boolean.TRUE.equals(values.get("anomaly"));
    }

    private static String joinReasons(List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) return null;
        return String.join("; ", reasons);
    }

    private String toJsonSafe(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("serializationError", ex.getMessage());
            fallback.put("valueType", value.getClass().getName());
            try {
                return objectMapper.writeValueAsString(fallback);
            } catch (JsonProcessingException ignored) {
                return "{\"serializationError\":\"failed\"}";
            }
        }
    }
}
