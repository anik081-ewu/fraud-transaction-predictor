package com.ftd.fraud_transaction_detector.aml.profile.domain;

import com.ftd.fraud_transaction_detector.fraud.dto.FraudPredictionResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustedLearningPolicyTest {

    private final TrustedLearningPolicy policy = new TrustedLearningPolicy();

    @Test
    void acceptsOnlyNonSuspiciousNormalOrLowTransactions() {
        assertTrue(policy.allows(response(false, "NORMAL")));
        assertTrue(policy.allows(response(false, "LOW")));
        assertFalse(policy.allows(response(true, "MEDIUM")));
        assertFalse(policy.allows(response(true, "HIGH")));
        assertFalse(policy.allows(response(false, "HIGH")));
    }

    private FraudPredictionResponse response(boolean suspicious, String riskLevel) {
        return new FraudPredictionResponse(
                "T-1", "A-1", suspicious, riskLevel, 0,
                Map.of(), Map.of(), List.of(), "ALLOW"
        );
    }
}
