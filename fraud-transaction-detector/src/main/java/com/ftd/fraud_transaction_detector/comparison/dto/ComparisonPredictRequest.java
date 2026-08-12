package com.ftd.fraud_transaction_detector.comparison.dto;

import com.ftd.fraud_transaction_detector.fraud.dto.FraudPredictionRequest;

import java.util.List;

public record ComparisonPredictRequest(
        FraudPredictionRequest.TransactionDto transaction,
        FraudPredictionRequest.CustomerDto customer,
        FraudPredictionRequest.AccountProfileDto accountProfile,
        String modelsDir,
        List<String> modelNames
) {
}
