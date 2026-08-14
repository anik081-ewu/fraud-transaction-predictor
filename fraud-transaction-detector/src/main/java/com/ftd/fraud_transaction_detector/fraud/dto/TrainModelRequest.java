package com.ftd.fraud_transaction_detector.fraud.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record TrainModelRequest(
        String source,
        String requestedBy,
        List<TrainingTransaction> transactions,
        Map<String, Object> hyperparams,
        List<String> modelNames,
        String outputSubdir,
        List<TrainingTransaction> evaluationTransactions,
        String learningMode
) {
    public TrainModelRequest(
            String source,
            String requestedBy,
            List<TrainingTransaction> transactions,
            Map<String, Object> hyperparams,
            List<String> modelNames,
            String outputSubdir,
            List<TrainingTransaction> evaluationTransactions
    ) {
        this(source, requestedBy, transactions, hyperparams, modelNames, outputSubdir,
                evaluationTransactions, "UNSUPERVISED");
    }

    public record TrainingTransaction(
            String transactionId,
            String accountId,
            BigDecimal transactionAmount,
            String transactionType,
            LocalDateTime transactionDate,
            String location,
            String channel,
            Integer customerAge,
            String customerOccupation,
            Integer loginAttempts,
            BigDecimal accountBalance,
            Boolean fraudLabel
    ) {
        public TrainingTransaction(
                String transactionId,
                String accountId,
                BigDecimal transactionAmount,
                String transactionType,
                LocalDateTime transactionDate,
                String location,
                String channel,
                Integer customerAge,
                String customerOccupation,
                Integer loginAttempts,
                BigDecimal accountBalance
        ) {
            this(transactionId, accountId, transactionAmount, transactionType, transactionDate,
                    location, channel, customerAge, customerOccupation, loginAttempts, accountBalance, null);
        }
    }
}
