package com.ftd.fraud_transaction_detector.comparison.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.comparison.client.ComparisonPredictionClient;
import com.ftd.fraud_transaction_detector.comparison.dto.*;
import com.ftd.fraud_transaction_detector.comparison.entity.AnomalyConfig;
import com.ftd.fraud_transaction_detector.comparison.repo.AnomalyConfigRepository;
import com.ftd.fraud_transaction_detector.config.entity.AppConfig;
import com.ftd.fraud_transaction_detector.config.repo.AppConfigRepository;
import com.ftd.fraud_transaction_detector.fraud.dto.FraudPredictionRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;

@Service
public class SimulationService {

    private final AnomalyConfigRepository anomalyConfigRepository;
    private final AppConfigRepository appConfigRepository;
    private final ComparisonPredictionClient comparisonPredictionClient;
    private final ObjectMapper objectMapper;

    public SimulationService(
            AnomalyConfigRepository anomalyConfigRepository,
            AppConfigRepository appConfigRepository,
            ComparisonPredictionClient comparisonPredictionClient,
            ObjectMapper objectMapper
    ) {
        this.anomalyConfigRepository = anomalyConfigRepository;
        this.appConfigRepository = appConfigRepository;
        this.comparisonPredictionClient = comparisonPredictionClient;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public SimulationResponse simulate(SimulationRequest request) {
        if (request == null || request.transaction() == null || request.accountProfile() == null) {
            throw new IllegalArgumentException("Transaction and account profile are required");
        }

        AnomalyConfig config = resolveConfig(request.anomalyConfigId());
        long historyCount = request.accountProfile().userTxnCount() == null ? 0 : request.accountProfile().userTxnCount();
        ColdStartDecision coldStartDecision = evaluateColdStart(historyCount);
        if (coldStartDecision.applied()) {
            return new SimulationResponse(
                    request.transaction().transactionId(),
                    request.transaction().accountId(),
                    config.getConfigName(),
                    true,
                    false,
                    coldStartDecision.riskLevel(),
                    0,
                    Map.of("skipped", Map.of("reason", coldStartDecision.reason())),
                    Map.of("coldStart", true, "userTxnCount", historyCount, "minHistoryRequired", coldStartDecision.minHistory()),
                    List.of(coldStartDecision.reason()),
                    coldStartDecision.recommendedAction()
            );
        }

        List<String> enabledModels = readEnabledModels(config.getEnabledModelsJson());
        ComparisonPredictResponse compareResponse = comparisonPredictionClient.compare(new ComparisonPredictRequest(
                request.transaction(),
                request.customer() == null ? new FraudPredictionRequest.CustomerDto(null, null) : request.customer(),
                request.accountProfile(),
                config.getArtifactBasePath(),
                enabledModels
        ));

        int votes = (int) compareResponse.modelResults().values().stream()
                .filter(result -> Boolean.TRUE.equals(result.get("anomaly")))
                .count();

        String riskLevel = determineRiskLevel(votes, config);
        boolean suspicious = votes >= config.getSuspiciousVoteThreshold();
        String recommendedAction = suspicious ? "ALLOW_AND_ALERT" : "ALLOW";
        if ("HIGH".equalsIgnoreCase(riskLevel)) {
            recommendedAction = "HOLD_FOR_REVIEW";
        } else if ("LOW".equalsIgnoreCase(riskLevel)) {
            recommendedAction = "ALLOW_AND_LOG";
        }

        List<String> reasons = new ArrayList<>(compareResponse.reasons());
        reasons.add("Simulation config: " + config.getConfigName());

        return new SimulationResponse(
                compareResponse.transactionId(),
                compareResponse.accountId(),
                config.getConfigName(),
                false,
                suspicious,
                riskLevel,
                votes,
                compareResponse.modelResults(),
                compareResponse.featureSummary(),
                reasons,
                recommendedAction
        );
    }

    private AnomalyConfig resolveConfig(Long anomalyConfigId) {
        if (anomalyConfigId != null) {
            return anomalyConfigRepository.findById(anomalyConfigId)
                    .orElseThrow(() -> new IllegalArgumentException("Anomaly config not found: " + anomalyConfigId));
        }
        return anomalyConfigRepository.findFirstByIsActiveTrueOrderByUpdatedAtDescIdDesc()
                .orElseThrow(() -> new IllegalArgumentException("No active anomaly config found"));
    }

    private ColdStartDecision evaluateColdStart(long historyCount) {
        boolean enabled = Boolean.parseBoolean(configValue("ml.cold_start.enabled", "true"));
        int minHistory = parseInt(configValue("ml.min_transaction_count_before_predict", "5"), 5);
        if (!enabled || historyCount >= minHistory) {
            return new ColdStartDecision(false, null, null, null, minHistory);
        }
        return new ColdStartDecision(
                true,
                configValue("ml.cold_start.default_risk_level", "NORMAL"),
                configValue("ml.cold_start.default_recommended_action", "ALLOW"),
                configValue("ml.cold_start.reason_message", "Insufficient transaction history for ML prediction."),
                minHistory
        );
    }

    private String determineRiskLevel(int votes, AnomalyConfig config) {
        if (votes >= config.getHighRiskVoteThreshold()) {
            return "HIGH";
        }
        if (votes >= config.getMediumRiskVoteThreshold()) {
            return "MEDIUM";
        }
        if (votes >= config.getSuspiciousVoteThreshold()) {
            return "LOW";
        }
        return "NORMAL";
    }

    private List<String> readEnabledModels(String rawJson) {
        try {
            return objectMapper.readValue(rawJson, new TypeReference<>() {});
        } catch (IOException ex) {
            return List.of();
        }
    }

    private String configValue(String key, String defaultValue) {
        return appConfigRepository.findById(key).map(AppConfig::getConfigValue).orElse(defaultValue);
    }

    private int parseInt(String raw, int defaultValue) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private record ColdStartDecision(
            boolean applied,
            String riskLevel,
            String recommendedAction,
            String reason,
            int minHistory
    ) {
    }
}
