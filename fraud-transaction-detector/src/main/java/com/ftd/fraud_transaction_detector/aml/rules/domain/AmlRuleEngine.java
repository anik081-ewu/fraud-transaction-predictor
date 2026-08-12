package com.ftd.fraud_transaction_detector.aml.rules.domain;

import com.ftd.fraud_transaction_detector.aml.feature.domain.TransactionFeatureVector;

public interface AmlRuleEngine {
    RuleEngineResult evaluate(TransactionFeatureVector features, RuleEvaluationContext context);
}
