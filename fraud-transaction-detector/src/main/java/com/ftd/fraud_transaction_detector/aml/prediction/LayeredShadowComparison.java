package com.ftd.fraud_transaction_detector.aml.prediction;

import com.ftd.fraud_transaction_detector.aml.behaviour.customer.CustomerBehaviourScore;
import com.ftd.fraud_transaction_detector.aml.behaviour.peer.PeerBehaviourScore;
import com.ftd.fraud_transaction_detector.aml.model.domain.MlModelScores;
import com.ftd.fraud_transaction_detector.aml.risk.domain.FinalRiskResult;
import com.ftd.fraud_transaction_detector.aml.rules.domain.RuleEngineResult;

import java.time.Instant;
import java.util.UUID;

public record LayeredShadowComparison(
        UUID shadowPredictionId,
        String transactionId,
        String accountId,
        String featureVersion,
        String legacyRiskLevel,
        boolean legacySuspicious,
        int legacyAnomalyVotes,
        CustomerBehaviourScore customerScore,
        PeerBehaviourScore peerScore,
        MlModelScores modelScores,
        RuleEngineResult ruleResult,
        FinalRiskResult layeredResult,
        boolean suspiciousChanged,
        boolean riskLevelChanged,
        boolean alertOverlap,
        Instant evaluatedAt,
        long durationMs
) {
}
