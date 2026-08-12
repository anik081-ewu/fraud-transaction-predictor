package com.ftd.fraud_transaction_detector.aml.prediction;

import com.ftd.fraud_transaction_detector.aml.deployment.domain.LayeredDeploymentPointer;
import com.ftd.fraud_transaction_detector.fraud.dto.FraudPredictionResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LayeredProductionResponseFactory {

    public FraudPredictionResponse create(
            FraudPredictionResponse legacy,
            LayeredShadowComparison comparison,
            LayeredDeploymentPointer pointer
    ) {
        var layered = comparison.layeredResult();
        Map<String, Object> modelResults = new LinkedHashMap<>();
        if (legacy.modelResults() != null) modelResults.putAll(legacy.modelResults());
        modelResults.put("LayeredRiskArchitecture", Map.of(
                "productionDecision", true,
                "riskPolicyVersion", layered.riskPolicyVersion(),
                "finalRiskScore", layered.finalRiskScore(),
                "riskLevel", layered.riskLevel().name(),
                "hardRuleOverride", layered.hardRuleOverride(),
                "componentScores", layered.componentScores(),
                "validationId", pointer.validationId().toString(),
                "canaryPercentage", pointer.canaryPercentage()
        ));
        Map<String, Object> summary = new LinkedHashMap<>();
        if (legacy.featureSummary() != null) summary.putAll(legacy.featureSummary());
        summary.put("productionArchitecture", "LAYERED_WEIGHTED_RISK_V2");
        summary.put("peerGroupCode", pointer.peerGroupCode());
        summary.put("riskPolicyVersion", layered.riskPolicyVersion());
        summary.put("finalRiskScore", layered.finalRiskScore());
        summary.put("legacyRiskLevel", legacy.riskLevel());
        summary.put("legacySuspicious", legacy.suspicious());
        List<String> reasons = new ArrayList<>(layered.reasonCodes());
        reasons.add("LAYERED_PRODUCTION_CANARY");
        reasons.add("LAYERED_SCORING_DOES_NOT_USE_EQUAL_MODEL_VOTES");
        return new FraudPredictionResponse(
                legacy.transactionId(), legacy.accountId(), layered.suspicious(),
                layered.riskLevel().name(), 0, modelResults, summary,
                reasons.stream().distinct().toList(), recommendedAction(layered.riskLevel().name())
        );
    }

    private String recommendedAction(String riskLevel) {
        if ("HIGH".equals(riskLevel)) return "HOLD_FOR_REVIEW";
        if ("MEDIUM".equals(riskLevel)) return "ALLOW_AND_ALERT";
        if ("LOW".equals(riskLevel)) return "ALLOW_AND_LOG";
        return "ALLOW";
    }
}
