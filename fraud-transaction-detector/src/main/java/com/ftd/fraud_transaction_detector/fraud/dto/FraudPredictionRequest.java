package com.ftd.fraud_transaction_detector.fraud.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FraudPredictionRequest(
        TransactionDto transaction,
        CustomerDto customer,
        AccountProfileDto accountProfile
) {
    public record TransactionDto(
            String transactionId,
            String accountId,
            BigDecimal transactionAmount,
            String transactionType,
            LocalDateTime transactionDate,
            String location,
            String channel,
            Integer loginAttempts,
            BigDecimal accountBalance
    ) {
    }

    public record CustomerDto(
            Integer customerAge,
            String customerOccupation
    ) {
    }

    public record AccountProfileDto(
            LocalDateTime previousTransactionDate,
            String previousLocation,
            BigDecimal userAvgAmount,
            BigDecimal userMaxAmount,
            BigDecimal userAmountStd,
            Long userTxnCount,
            BigDecimal rolling7dAvgAmount,
            BigDecimal rolling30dAvgAmount
    ) {
    }
}

