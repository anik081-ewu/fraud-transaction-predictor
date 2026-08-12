package com.ftd.fraud_transaction_detector.aml.risk.domain;

import com.ftd.fraud_transaction_detector.aml.scoring.domain.RiskBand;

import java.util.List;

public record FinalRiskResult(
        String riskPolicyVersion,
        double finalRiskScore,
        RiskBand riskLevel,
        boolean suspicious,
        boolean hardRuleOverride,
        ComponentScores componentScores,
        List<String> reasonCodes
) {
    public FinalRiskResult {
        if (riskPolicyVersion == null || riskPolicyVersion.isBlank()) {
            throw new IllegalArgumentException("riskPolicyVersion is required");
        }
        if (!Double.isFinite(finalRiskScore) || finalRiskScore < 0.0 || finalRiskScore > 1.0) {
            throw new IllegalArgumentException("finalRiskScore must be between 0.0 and 1.0");
        }
        if (riskLevel == null) throw new IllegalArgumentException("riskLevel is required");
        if (componentScores == null) throw new IllegalArgumentException("componentScores are required");
        riskPolicyVersion = riskPolicyVersion.trim();
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }
}
