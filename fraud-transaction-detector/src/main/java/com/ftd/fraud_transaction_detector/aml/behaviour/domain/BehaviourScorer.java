package com.ftd.fraud_transaction_detector.aml.behaviour.domain;

import com.ftd.fraud_transaction_detector.aml.feature.domain.TransactionFeatureVector;

public interface BehaviourScorer {
    BehaviourScore score(TransactionFeatureVector features, BehaviourScoringContext context);
}
