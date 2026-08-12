package com.ftd.fraud_transaction_detector.aml.behaviour.domain;

import com.ftd.fraud_transaction_detector.aml.scoring.domain.NormalizedScore;
import com.ftd.fraud_transaction_detector.aml.scoring.domain.RiskBand;

import java.util.List;

public interface BehaviourScore {
    NormalizedScore score();
    RiskBand riskBand();
    double confidence();
    List<String> reasonCodes();
}
