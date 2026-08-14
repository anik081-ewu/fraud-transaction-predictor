package com.ftd.fraud_transaction_detector.comparison.service;

import com.ftd.fraud_transaction_detector.comparison.dto.ComparisonPredictResponse;
import com.ftd.fraud_transaction_detector.comparison.entity.AnomalyConfig;
import com.ftd.fraud_transaction_detector.comparison.repo.AnomalyConfigRepository;
import com.ftd.fraud_transaction_detector.fraud.client.PersistedFeaturePredictionClient;
import com.ftd.fraud_transaction_detector.fraud.dto.FraudPredictionResponse;
import com.ftd.fraud_transaction_detector.fraud.dto.PersistedFeaturePredictRequest;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TransactionFeatureVector;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConfiguredAnomalyPredictionService {

    // AUTOENCODER is the risk-policy key exposed by AppConfigService.BATCH_MODEL_KEYS and
    // must map here, or an enabled policy trains a model that never reaches the decision:
    // decisionResults() drops unmapped keys silently.
    private static final Map<String, String> CONFIG_MODEL_TO_RESPONSE_MODEL = Map.of(
            "ISOLATION_FOREST", "IsolationForest",
            "LOCAL_OUTLIER_FACTOR", "LOF",
            "AUTOENCODER", "Autoencoder",
            "XGBOOST_CLASSIFIER", "XGBoost",
            "RANDOM_FOREST_CLASSIFIER", "RandomForestClassifier",
            "LOGISTIC_REGRESSION", "LogisticRegression"
    );

    private static final Map<String, String> CONFIG_MODEL_TO_BATCH_MODEL = Map.of(
            "ISOLATION_FOREST", "IsolationForest",
            "LOCAL_OUTLIER_FACTOR", "LOF",
            "AUTOENCODER", "Autoencoder",
            "XGBOOST_CLASSIFIER", "XGBoost",
            "RANDOM_FOREST_CLASSIFIER", "RandomForestClassifier",
            "LOGISTIC_REGRESSION", "LogisticRegression"
    );

    private final AnomalyConfigRepository anomalyConfigRepository;
    private final PersistedFeaturePredictionClient persistedFeaturePredictionClient;
    private final AppConfigService appConfigService;

    public ConfiguredAnomalyPredictionService(
            AnomalyConfigRepository anomalyConfigRepository,
            PersistedFeaturePredictionClient persistedFeaturePredictionClient,
            AppConfigService appConfigService
    ) {
        this.anomalyConfigRepository = anomalyConfigRepository;
        this.persistedFeaturePredictionClient = persistedFeaturePredictionClient;
        this.appConfigService = appConfigService;
    }

    public FraudPredictionResponse predict(TransactionFeatureVector featureVector) {
        return predictConfigured(featureVector);
    }

    public FraudPredictionResponse predictBatchFallback(TransactionFeatureVector featureVector) {
        return predictConfigured(featureVector);
    }

    private FraudPredictionResponse predictConfigured(TransactionFeatureVector featureVector) {
        AnomalyConfig config = anomalyConfigRepository.findFirstByIsActiveTrueOrderByUpdatedAtDescIdDesc()
                .orElse(null);
        boolean configured = config != null
                && config.getArtifactBasePath() != null
                && !config.getArtifactBasePath().isBlank();
        Map<String, Double> configuredModels = appConfigService.getEnabledRiskPolicyModelWeights();
        List<String> enabledModels = resolveEnabledModels(configuredModels);

        ComparisonPredictResponse response = persistedFeaturePredictionClient.predict(
                persistedRequest(
                        featureVector, configured ? config.getArtifactBasePath() : null,
                        enabledModels
                )
        );
        if (response.modelResults() == null || response.modelResults().isEmpty()) {
            return unavailable(featureVector);
        }

        List<Map<String, Object>> decisionResults = decisionResults(response.modelResults(), configuredModels);
        if (decisionResults.isEmpty()) {
            return unavailable(featureVector);
        }
        int votes = (int) decisionResults.stream()
                .filter(result -> Boolean.TRUE.equals(result.get("anomaly")))
                .count();
        int modelCount = decisionResults.size();
        int threshold = suspiciousThreshold(modelCount);
        boolean suspicious = votes >= threshold;
        String riskLevel = riskLevel(votes, modelCount, suspicious, configured);
        String recommendedAction = "HIGH".equals(riskLevel)
                ? "HOLD_FOR_REVIEW"
                : suspicious ? "ALLOW_AND_ALERT"
                : "LOW".equals(riskLevel) ? "ALLOW_AND_LOG" : "ALLOW";
        List<String> reasons = new ArrayList<>(response.reasons() == null ? List.of() : response.reasons());
        reasons.add(configured
                ? "Persisted feature vector scored with active anomaly config " + config.getConfigName()
                : "Persisted feature vector scored with the default production model set");

        Map<String, Object> modelResults = new LinkedHashMap<>();
        response.modelResults().forEach(modelResults::put);
        return new FraudPredictionResponse(
                response.transactionId(), response.accountId(), suspicious, riskLevel,
                votes, modelResults, response.featureSummary(), reasons, recommendedAction
        );
    }

    private PersistedFeaturePredictRequest persistedRequest(
            TransactionFeatureVector vector,
            String modelsDir,
            List<String> modelNames
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("featureVersion", vector.featureVersion());
        summary.put("modelFeatureSchema", vector.modelFeatureSchema());
        summary.put("profileConfidence", vector.profile().confidence());
        summary.put("trustedHistoryCount", vector.profile().trustedHistoryCount());
        summary.put("amountVsLast30Average", vector.amount().amountVsLast30Average());
        summary.put("amountZScoreLast30", vector.amount().amountZScoreLast30());
        summary.put("newLocation", vector.novelty().newLocation());
        summary.put("transactionCount24Hours", vector.velocity().transactionCount24Hours());
        return new PersistedFeaturePredictRequest(
                vector.transactionId(), vector.accountId(), vector.featureVersion(),
                vector.modelFeatureSchema(), vector.modelFeatures(), summary,
                List.of(), modelsDir, modelNames,
                null, null, null, null, null, null,
                appConfigService.getLearningMode()
        );
    }

    private String riskLevel(int votes, int modelCount, boolean suspicious, boolean configured) {
        if (votes >= modelCount) {
            return "HIGH";
        }
        if (suspicious) {
            return "MEDIUM";
        }
        return votes > 0 ? "LOW" : "NORMAL";
    }

    private List<String> resolveEnabledModels(Map<String, Double> configuredModels) {
        // distinct(): guards against two config keys resolving to one Python model, which
        // would otherwise request it twice and double-count its vote.
        return configuredModels.entrySet().stream()
                .filter(entry -> CONFIG_MODEL_TO_BATCH_MODEL.containsKey(entry.getKey()))
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .map(Map.Entry::getKey)
                .map(CONFIG_MODEL_TO_BATCH_MODEL::get)
                .distinct()
                .toList();
    }

    private List<Map<String, Object>> decisionResults(
            Map<String, Map<String, Object>> modelResults,
            Map<String, Double> configuredModels
    ) {
        return configuredModels.keySet().stream()
                .map(CONFIG_MODEL_TO_RESPONSE_MODEL::get)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .map(modelResults::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private int suspiciousThreshold(int modelCount) {
        return Math.max(1, (int) Math.ceil(modelCount / 2.0));
    }

    private FraudPredictionResponse unavailable(TransactionFeatureVector vector) {
        String reason = "Production ML scoring unavailable; transaction allowed and logged for operational review";
        return new FraudPredictionResponse(
                vector.transactionId(), vector.accountId(), false, "NORMAL", 0, Map.of(),
                Map.of(
                        "featureVersion", vector.featureVersion(),
                        "scoringContract", "PERSISTED_FEATURES_V2",
                        "predictionUnavailable", true
                ),
                List.of(reason), "ALLOW_AND_LOG"
        );
    }
}
