package com.ftd.fraud_transaction_detector.aml.feature.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public record HistoricalTransaction(
        String transactionId,
        BigDecimal amount,
        LocalDateTime transactionDate,
        String transactionType,
        String channel,
        String location,
        String beneficiaryId,
        String deviceId,
        boolean trusted
) {
    public HistoricalTransaction {
        Objects.requireNonNull(transactionId, "transactionId is required");
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(transactionDate, "transactionDate is required");
    }
}
