package com.ftd.fraud_transaction_detector.aml.learning.domain;

import com.ftd.fraud_transaction_detector.fraud.dto.FraudPredictionResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearningEligibilityPolicyTest {

    private final LearningEligibilityPolicy policy = new LearningEligibilityPolicy();

    @Test
    void acceptsNonSuspiciousScoredTransaction() {
        var decision = policy.evaluate(response(false, "LOW", Map.of("IsolationForest", Map.of())));

        assertEquals(LearningEligibilityStatus.LEARN_IMMEDIATELY, decision.status());
        assertTrue(decision.eligibleForTrustedProfile());
    }

    @Test
    void holdsSuspiciousTransactionForReview() {
        var decision = policy.evaluate(response(true, "HIGH", Map.of("IsolationForest", Map.of())));

        assertEquals(LearningEligibilityStatus.WAIT_FOR_REVIEW, decision.status());
        assertFalse(decision.eligibleForBatchTraining());
    }

    @Test
    void delaysLearningWhenPredictionIsUnavailable() {
        var decision = policy.evaluate(response(false, "NORMAL", Map.of()));

        assertEquals(LearningEligibilityStatus.DELAYED_LEARNING, decision.status());
        assertFalse(decision.eligibleForTrustedProfile());
    }

    private FraudPredictionResponse response(
            boolean suspicious,
            String riskLevel,
            Map<String, Object> modelResults
    ) {
        return new FraudPredictionResponse(
                "T-1", "A-1", suspicious, riskLevel, 0,
                modelResults, Map.of(), List.of(), "ALLOW"
        );
    }
}
