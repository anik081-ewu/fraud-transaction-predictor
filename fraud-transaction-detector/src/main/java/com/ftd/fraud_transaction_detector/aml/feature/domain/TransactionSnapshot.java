package com.ftd.fraud_transaction_detector.aml.feature.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public record TransactionSnapshot(
        String transactionId,
        String customerId,
        String accountId,
        BigDecimal amount,
        BigDecimal balance,
        String transactionType,
        LocalDateTime transactionDate,
        String channel,
        String location,
        String beneficiaryId,
        String deviceId,
        int loginAttempts,
        String customerOccupation
) {
    public TransactionSnapshot {
        Objects.requireNonNull(transactionId, "transactionId is required");
        Objects.requireNonNull(customerId, "customerId is required");
        Objects.requireNonNull(accountId, "accountId is required");
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(transactionDate, "transactionDate is required");
    }
}
