package com.ftd.fraud_transaction_detector.comparison.dto;

import java.util.List;

public record RiskPolicyConfigUpdateRequest(
        double customerBehaviourWeight,
        double peerBehaviourWeight,
        double mlEnsembleWeight,
        double rulesWeight,
        CustomerBehaviourSubWeightsRequest customerBehaviourSubWeights,
        PeerBehaviourSubWeightsRequest peerBehaviourSubWeights,
        AmlRuleThresholdsRequest amlRuleThresholds,
        List<RiskPolicyModelConfigRequest> models,
        String incrementalSchedule,
        String batchSchedule,
        double lowRiskThreshold,
        double mediumRiskThreshold,
        double highRiskThreshold
) {
    public record CustomerBehaviourSubWeightsRequest(
            double amount,
            double novelty,
            double frequency,
            double timeGap,
            double unusualHour
    ) {}

    public record PeerBehaviourSubWeightsRequest(
            double amount,
            double frequency,
            double expectedTurnover
    ) {}

    public record AmlRuleThresholdsRequest(
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
