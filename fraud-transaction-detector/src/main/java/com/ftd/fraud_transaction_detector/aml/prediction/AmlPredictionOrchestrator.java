package com.ftd.fraud_transaction_detector.aml.prediction;

import com.ftd.fraud_transaction_detector.aml.feature.domain.TransactionFeatureVector;
import com.ftd.fraud_transaction_detector.aml.risk.domain.FinalRiskResult;
import com.ftd.fraud_transaction_detector.comparison.service.ConfiguredAnomalyPredictionService;
import com.ftd.fraud_transaction_detector.fraud.dto.FraudPredictionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AmlPredictionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AmlPredictionOrchestrator.class);

    private final ConfiguredAnomalyPredictionService mlPredictionService;
    private final LayeredShadowScoringService scoringService;

    public AmlPredictionOrchestrator(
            ConfiguredAnomalyPredictionService mlPredictionService,
            LayeredShadowScoringService scoringService
    ) {
        this.mlPredictionService = mlPredictionService;
        this.scoringService = scoringService;
    }

    public FraudPredictionResponse predict(TransactionFeatureVector featureVector) {
        FraudPredictionResponse mlResponse = mlPredictionService.predict(featureVector);
        try {
            FinalRiskResult result = scoringService.score(featureVector, mlResponse);
            return buildResponse(featureVector, mlResponse, result);
        } catch (Exception exception) {
            log.error(
                    "Layered scoring failed for transaction {}; falling back to raw ML response",
                    featureVector.transactionId(), exception
            );
            return mlResponse;
        }
    }

    private FraudPredictionResponse buildResponse(
            TransactionFeatureVector featureVector,
            FraudPredictionResponse mlResponse,
            FinalRiskResult result
    ) {
        Map<String, Object> modelResults = new LinkedHashMap<>();
        if (mlResponse.modelResults() != null) modelResults.putAll(mlResponse.modelResults());
        modelResults.put("LayeredRiskArchitecture", Map.of(
                "productionDecision", true,
                "riskPolicyVersion", result.riskPolicyVersion(),
                "finalRiskScore", result.finalRiskScore(),
                "riskLevel", result.riskLevel().name(),
                "hardRuleOverride", result.hardRuleOverride(),
                "componentScores", result.componentScores()
        ));
        Map<String, Object> summary = new LinkedHashMap<>();
        if (mlResponse.featureSummary() != null) summary.putAll(mlResponse.featureSummary());
        summary.put("productionArchitecture", "LAYERED_WEIGHTED_RISK_V2");
        summary.put("riskPolicyVersion", result.riskPolicyVersion());
        summary.put("finalRiskScore", result.finalRiskScore());
        List<String> reasons = new ArrayList<>(result.reasonCodes());
        return new FraudPredictionResponse(
                featureVector.transactionId(), featureVector.accountId(), result.suspicious(),
                result.riskLevel().name(), mlResponse.anomalyVotes(), modelResults, summary,
                reasons.stream().distinct().toList(), recommendedAction(result.riskLevel().name())
        );
    }

    private String recommendedAction(String riskLevel) {
        if ("HIGH".equals(riskLevel)) return "HOLD_FOR_REVIEW";
        if ("MEDIUM".equals(riskLevel)) return "ALLOW_AND_ALERT";
        if ("LOW".equals(riskLevel)) return "ALLOW_AND_LOG";
        return "ALLOW";
    }
}
