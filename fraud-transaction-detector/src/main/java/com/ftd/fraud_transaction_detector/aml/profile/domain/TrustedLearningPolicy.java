package com.ftd.fraud_transaction_detector.aml.profile.domain;

import com.ftd.fraud_transaction_detector.fraud.dto.FraudPredictionResponse;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class TrustedLearningPolicy {

    private static final Set<String> TRUSTED_RISK_LEVELS = Set.of("NORMAL", "LOW");

    public boolean allows(FraudPredictionResponse response) {
        return response != null
                && !response.suspicious()
                && response.riskLevel() != null
                && TRUSTED_RISK_LEVELS.contains(response.riskLevel().toUpperCase());
    }
}
