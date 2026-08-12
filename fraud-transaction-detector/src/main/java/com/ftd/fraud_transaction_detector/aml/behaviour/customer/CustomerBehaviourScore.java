package com.ftd.fraud_transaction_detector.aml.behaviour.customer;

import com.ftd.fraud_transaction_detector.aml.behaviour.domain.BehaviourScore;
import com.ftd.fraud_transaction_detector.aml.scoring.domain.NormalizedScore;
import com.ftd.fraud_transaction_detector.aml.scoring.domain.RiskBand;

import java.util.List;

public record CustomerBehaviourScore(
        NormalizedScore score,
        RiskBand riskBand,
        double confidence,
        List<String> reasonCodes
) implements BehaviourScore {
    public CustomerBehaviourScore {
        if (score == null) throw new IllegalArgumentException("score is required");
        if (riskBand == null) throw new IllegalArgumentException("riskBand is required");
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }
}
