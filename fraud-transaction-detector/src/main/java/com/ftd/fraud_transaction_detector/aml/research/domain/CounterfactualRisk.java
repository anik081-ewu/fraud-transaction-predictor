package com.ftd.fraud_transaction_detector.aml.research.domain;

import com.ftd.fraud_transaction_detector.aml.scoring.domain.RiskBand;

public record CounterfactualRisk(double score, RiskBand riskBand, boolean suspicious, boolean hardRuleOverride) {
}
