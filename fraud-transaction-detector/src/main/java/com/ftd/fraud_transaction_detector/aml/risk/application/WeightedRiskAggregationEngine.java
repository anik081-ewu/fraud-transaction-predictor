package com.ftd.fraud_transaction_detector.aml.risk.application;

import com.ftd.fraud_transaction_detector.aml.behaviour.customer.CustomerBehaviourScore;
import com.ftd.fraud_transaction_detector.aml.behaviour.peer.PeerBehaviourScore;
import com.ftd.fraud_transaction_detector.aml.model.domain.MlModelScore;
import com.ftd.fraud_transaction_detector.aml.model.domain.MlModelScores;
import com.ftd.fraud_transaction_detector.aml.risk.domain.ComponentScores;
import com.ftd.fraud_transaction_detector.aml.risk.domain.FinalRiskResult;
import com.ftd.fraud_transaction_detector.aml.risk.domain.RiskAggregationEngine;
import com.ftd.fraud_transaction_detector.aml.risk.domain.RiskPolicy;
import com.ftd.fraud_transaction_detector.aml.rules.domain.RuleEngineResult;
import com.ftd.fraud_transaction_detector.aml.scoring.domain.RiskBand;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class WeightedRiskAggregationEngine implements RiskAggregationEngine {

    @Override
    public FinalRiskResult aggregate(
            CustomerBehaviourScore customerScore,
            PeerBehaviourScore peerScore,
            MlModelScores modelScores,
            RuleEngineResult ruleResult,
            RiskPolicy policy,
            Map<String, Double> modelAllocations
    ) {
        Objects.requireNonNull(customerScore, "customerScore is required");
        Objects.requireNonNull(peerScore, "peerScore is required");
        Objects.requireNonNull(modelScores, "modelScores are required");
        Objects.requireNonNull(ruleResult, "ruleResult is required");
        Objects.requireNonNull(policy, "policy is required");

        List<String> operationalReasons = new ArrayList<>();
        List<String> mlReasons = new ArrayList<>();

        double ensembleScore = computeEnsembleScore(
                modelScores, modelAllocations, policy.mlEnsembleWeight(), operationalReasons, mlReasons
        );

        ComponentScores components = new ComponentScores(
                customerScore.score().normalizedScore(),
                peerScore.score().normalizedScore(),
                ensembleScore,
                ruleResult.score().normalizedScore()
        );
        double weightedScore = clamp(
                components.customerBehaviour() * policy.customerBehaviourWeight()
                        + components.peerBehaviour() * policy.peerBehaviourWeight()
                        + components.mlEnsemble() * policy.mlEnsembleWeight()
                        + components.rules() * policy.rulesWeight()
        );
        boolean hardOverride = ruleResult.hardAlert();
        double finalScore = hardOverride
                ? Math.max(weightedScore, policy.highRiskThreshold())
                : weightedScore;
        RiskBand riskBand = hardOverride ? RiskBand.HIGH : riskBand(finalScore, policy);
        boolean suspicious = hardOverride || riskBand == RiskBand.MEDIUM || riskBand == RiskBand.HIGH;
        if (hardOverride) operationalReasons.add("HARD_RULE_OVERRIDE");

        List<String> reasons = Stream.of(
                        customerScore.reasonCodes().stream(),
                        peerScore.reasonCodes().stream(),
                        mlReasons.stream(),
                        ruleResult.reasonCodes().stream(),
                        operationalReasons.stream()
                )
                .flatMap(stream -> stream)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(reason -> !reason.isEmpty())
                .distinct()
                .sorted()
                .toList();

        return new FinalRiskResult(
                policy.version(), finalScore, riskBand, suspicious,
                hardOverride, components, reasons
        );
    }

    private double computeEnsembleScore(
            MlModelScores modelScores,
            Map<String, Double> modelAllocations,
            double mlEnsembleWeight,
            List<String> operationalReasons,
            List<String> mlReasons
    ) {
        if (modelAllocations == null || modelAllocations.isEmpty()) {
            if (mlEnsembleWeight > 0.0) {
                operationalReasons.add("ML_ENSEMBLE_SCORE_UNAVAILABLE");
            }
            return 0.0;
        }
        double totalWeight = 0.0;
        double weightedSum = 0.0;
        int selectedModelCount = 0;
        int availableModelCount = 0;
        int anomalyModelCount = 0;
        for (Map.Entry<String, Double> entry : modelAllocations.entrySet()) {
            double w = entry.getValue();
            if (w <= 0.0) continue;
            selectedModelCount++;
            MlModelScore score = find(modelScores, entry.getKey());
            if (score != null) {
                availableModelCount++;
                if (score.anomaly()) anomalyModelCount++;
                weightedSum += score.score().normalizedScore() * w;
                totalWeight += w;
                mlReasons.addAll(score.reasonCodes());
            } else {
                operationalReasons.add(entry.getKey() + "_SCORE_UNAVAILABLE");
            }
        }
        if (selectedModelCount > 0
                && availableModelCount == selectedModelCount
                && anomalyModelCount == selectedModelCount) {
            operationalReasons.add("ML_ENSEMBLE_UNANIMOUS_ANOMALY");
            return 1.0;
        }
        return totalWeight > 0.0 ? clamp(weightedSum / totalWeight) : 0.0;
    }

    private MlModelScore find(MlModelScores scores, String modelKey) {
        MlModelScore direct = scores.get(modelKey);
        if (direct != null) return direct;
        return scores.scores().values().stream()
                .filter(s -> modelKey.equals(s.modelType()))
                .findFirst()
                .orElse(null);
    }

    private RiskBand riskBand(double score, RiskPolicy policy) {
        if (score >= policy.highRiskThreshold()) return RiskBand.HIGH;
        if (score >= policy.mediumRiskThreshold()) return RiskBand.MEDIUM;
        if (score >= policy.lowRiskThreshold()) return RiskBand.LOW;
        return RiskBand.NORMAL;
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
