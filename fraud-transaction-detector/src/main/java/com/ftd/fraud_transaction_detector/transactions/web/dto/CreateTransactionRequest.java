package com.ftd.fraud_transaction_detector.transactions.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateTransactionRequest(
        @NotBlank String transactionId,
        @NotBlank String accountId,
        @NotNull BigDecimal transactionAmount,
        @NotBlank String transactionType,
        @NotNull LocalDateTime transactionDate,
        @NotBlank String location,
        @NotBlank String channel,
        Integer customerAge,
        String customerOccupation,
        Integer loginAttempts,
        BigDecimal accountBalance
) {
}
