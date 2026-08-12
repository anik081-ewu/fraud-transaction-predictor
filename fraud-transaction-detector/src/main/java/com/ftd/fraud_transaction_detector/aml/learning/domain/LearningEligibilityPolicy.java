package com.ftd.fraud_transaction_detector.aml.learning.domain;

import com.ftd.fraud_transaction_detector.fraud.dto.FraudPredictionResponse;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class LearningEligibilityPolicy {

    private static final Set<String> ACCEPTED_RISK_LEVELS = Set.of("NORMAL", "LOW");

    public LearningEligibilityDecision evaluate(FraudPredictionResponse response) {
        if (response == null || response.modelResults() == null || response.modelResults().isEmpty()) {
            return delayed("Prediction result unavailable; learning delayed to prevent baseline contamination");
        }
        if (response.suspicious() || !acceptedRisk(response.riskLevel())) {
            return waiting("Suspicious transaction requires analyst review before learning");
        }
        return immediate("Non-suspicious transaction accepted for trusted learning");
    }

    private boolean acceptedRisk(String riskLevel) {
        return riskLevel != null && ACCEPTED_RISK_LEVELS.contains(riskLevel.toUpperCase());
    }

    private LearningEligibilityDecision immediate(String reason) {
        return new LearningEligibilityDecision(
                LearningEligibilityStatus.LEARN_IMMEDIATELY, reason, true, true, true
        );
    }

    private LearningEligibilityDecision waiting(String reason) {
        return new LearningEligibilityDecision(
                LearningEligibilityStatus.WAIT_FOR_REVIEW, reason, false, false, false
        );
    }

    private LearningEligibilityDecision delayed(String reason) {
        return new LearningEligibilityDecision(
                LearningEligibilityStatus.DELAYED_LEARNING, reason, false, false, false
        );
    }
}
