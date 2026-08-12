package com.ftd.fraud_transaction_detector.aml.research.application;

import com.ftd.fraud_transaction_detector.aml.research.domain.AblationVariant;
import com.ftd.fraud_transaction_detector.aml.research.domain.CounterfactualRisk;
import com.ftd.fraud_transaction_detector.aml.research.domain.LayerScores;
import com.ftd.fraud_transaction_detector.aml.risk.domain.RiskPolicy;
import com.ftd.fraud_transaction_detector.aml.scoring.domain.RiskBand;
import org.springframework.stereotype.Component;

import static com.ftd.fraud_transaction_detector.aml.research.domain.AblationVariant.LayerComponent.CUSTOMER;
import static com.ftd.fraud_transaction_detector.aml.research.domain.AblationVariant.LayerComponent.ML_ENSEMBLE;
import static com.ftd.fraud_transaction_detector.aml.research.domain.AblationVariant.LayerComponent.PEER;
import static com.ftd.fraud_transaction_detector.aml.research.domain.AblationVariant.LayerComponent.RULES;

@Component
public class LayerAblationCalculator {

    public CounterfactualRisk calculate(LayerScores scores, RiskPolicy policy, AblationVariant variant) {
        double includedWeight = 0.0;
        double weightedScore = 0.0;
        if (variant.includes(CUSTOMER)) {
            includedWeight += policy.customerBehaviourWeight();
            weightedScore += scores.customerBehaviour() * policy.customerBehaviourWeight();
        }
        if (variant.includes(PEER)) {
            includedWeight += policy.peerBehaviourWeight();
            weightedScore += scores.peerBehaviour() * policy.peerBehaviourWeight();
        }
        if (variant.includes(ML_ENSEMBLE)) {
            includedWeight += policy.mlEnsembleWeight();
            weightedScore += scores.mlEnsemble() * policy.mlEnsembleWeight();
        }
        if (variant.includes(RULES)) {
            includedWeight += policy.rulesWeight();
            weightedScore += scores.rules() * policy.rulesWeight();
        }
        double normalized = includedWeight == 0.0 ? 0.0 : clamp(weightedScore / includedWeight);
        boolean hardOverride = variant.includes(RULES) && scores.hardRuleOverride();
        double finalScore = hardOverride ? Math.max(normalized, policy.highRiskThreshold()) : normalized;
        RiskBand band = hardOverride ? RiskBand.HIGH : band(finalScore, policy);
        return new CounterfactualRisk(
                finalScore, band, hardOverride || band == RiskBand.MEDIUM || band == RiskBand.HIGH, hardOverride
        );
    }

    private RiskBand band(double score, RiskPolicy policy) {
        if (score >= policy.highRiskThreshold()) return RiskBand.HIGH;
        if (score >= policy.mediumRiskThreshold()) return RiskBand.MEDIUM;
        if (score >= policy.lowRiskThreshold()) return RiskBand.LOW;
        return RiskBand.NORMAL;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
