package com.ftd.fraud_transaction_detector.comparison.dto;

import com.ftd.fraud_transaction_detector.comparison.entity.ComparisonScenario;
import com.ftd.fraud_transaction_detector.fraud.dto.FraudPredictionRequest;

import java.time.Instant;

public record ComparisonScenarioResponse(
        Long id,
        String scenarioNo,
        Long scenarioSetId,
        String scenarioName,
        String scenarioType,
        FraudPredictionRequest.TransactionDto transaction,
        FraudPredictionRequest.CustomerDto customer,
        FraudPredictionRequest.AccountProfileDto accountProfile,
        String expectedNotes,
        Instant createdAt
) {
    public static ComparisonScenarioResponse from(
            ComparisonScenario scenario,
            FraudPredictionRequest.TransactionDto transaction,
            FraudPredictionRequest.CustomerDto customer,
            FraudPredictionRequest.AccountProfileDto accountProfile
    ) {
        return new ComparisonScenarioResponse(
                scenario.getId(),
                scenario.getScenarioNo(),
                scenario.getScenarioSet().getId(),
                scenario.getScenarioName(),
                scenario.getScenarioType(),
                transaction,
                customer,
                accountProfile,
                scenario.getExpectedNotes(),
                scenario.getCreatedAt()
        );
    }
}
