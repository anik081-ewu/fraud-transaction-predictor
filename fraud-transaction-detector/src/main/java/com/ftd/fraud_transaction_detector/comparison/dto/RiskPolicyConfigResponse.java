package com.ftd.fraud_transaction_detector.comparison.dto;

import java.time.Instant;
import java.util.List;

public record RiskPolicyConfigResponse(
        String policyVersion,
        double customerBehaviourWeight,
        double peerBehaviourWeight,
        double mlEnsembleWeight,
        double rulesWeight,
        CustomerBehaviourSubWeights customerBehaviourSubWeights,
        PeerBehaviourSubWeights peerBehaviourSubWeights,
        AmlRuleThresholds amlRuleThresholds,
        List<RiskPolicyModelConfigResponse> models,
        String incrementalSchedule,
        String batchSchedule,
        double lowRiskThreshold,
        double mediumRiskThreshold,
        double highRiskThreshold,
        Instant updatedAt
) {
    public record CustomerBehaviourSubWeights(
            double amount,
            double novelty,
            double frequency,
            double timeGap,
            double unusualHour
    ) {}

    public record PeerBehaviourSubWeights(
            double amount,
            double frequency,
            double expectedTurnover
    ) {}

    public record AmlRuleThresholds(
            double reportingThreshold,
            int structuringCount24h,
            int rapidTxCount10m,
            int highTxCount1h,
            int multiBeneficiaryCount1h,
            int repeatedAmountCount24h,
            double highCustomerAmountRatio,
            double extremeCustomerAmountRatio,
            double highBalanceRatio,
            double highExpectedTurnoverRatio
    ) {}
}
