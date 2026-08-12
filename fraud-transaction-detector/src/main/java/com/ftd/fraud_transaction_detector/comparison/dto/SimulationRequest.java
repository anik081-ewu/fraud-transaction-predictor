package com.ftd.fraud_transaction_detector.comparison.dto;

import com.ftd.fraud_transaction_detector.fraud.dto.FraudPredictionRequest;

public record SimulationRequest(
        Long anomalyConfigId,
        FraudPredictionRequest.TransactionDto transaction,
        FraudPredictionRequest.CustomerDto customer,
        FraudPredictionRequest.AccountProfileDto accountProfile
) {
}
