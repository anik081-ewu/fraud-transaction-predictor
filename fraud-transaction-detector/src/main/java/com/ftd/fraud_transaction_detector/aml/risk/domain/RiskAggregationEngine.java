package com.ftd.fraud_transaction_detector.aml.risk.domain;

import com.ftd.fraud_transaction_detector.aml.behaviour.customer.CustomerBehaviourScore;
import com.ftd.fraud_transaction_detector.aml.behaviour.peer.PeerBehaviourScore;
import com.ftd.fraud_transaction_detector.aml.model.domain.MlModelScores;
import com.ftd.fraud_transaction_detector.aml.rules.domain.RuleEngineResult;

import java.util.Map;

public interface RiskAggregationEngine {
    FinalRiskResult aggregate(
            CustomerBehaviourScore customerScore,
            PeerBehaviourScore peerScore,
            MlModelScores modelScores,
            RuleEngineResult ruleResult,
            RiskPolicy policy,
            Map<String, Double> modelAllocations
    );
}
