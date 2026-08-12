package com.ftd.fraud_transaction_detector.fraud.service;

import com.ftd.fraud_transaction_detector.fraud.client.ScorePercentilesClient;
import com.ftd.fraud_transaction_detector.fraud.dto.ScorePercentilesRequest;
import com.ftd.fraud_transaction_detector.fraud.dto.ScorePercentilesResponse;
import com.ftd.fraud_transaction_detector.fraud.dto.TrainModelRequest;
import com.ftd.fraud_transaction_detector.transactions.entity.Transaction;
import com.ftd.fraud_transaction_detector.transactions.repo.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScorePercentilesService {

    private final TransactionRepository transactionRepository;
    private final ScorePercentilesClient scorePercentilesClient;

    public ScorePercentilesService(TransactionRepository transactionRepository, ScorePercentilesClient scorePercentilesClient) {
        this.transactionRepository = transactionRepository;
        this.scorePercentilesClient = scorePercentilesClient;
    }

    public ScorePercentilesResponse computeFromDatabase(String requestedBy) {
        List<Transaction> txns = transactionRepository.findAll();
        if (txns.isEmpty()) {
            throw new IllegalArgumentException("No transactions found in database");
        }
        List<TrainModelRequest.TrainingTransaction> payloadTxns = txns.stream()
                .map(ScorePercentilesService::toTrainingTxn)
                .toList();

        ScorePercentilesRequest req = new ScorePercentilesRequest(
                "SPRING_BOOT_DB",
                requestedBy == null || requestedBy.isBlank() ? "spring-boot" : requestedBy,
                payloadTxns
        );
        return scorePercentilesClient.compute(req);
    }

    private static TrainModelRequest.TrainingTransaction toTrainingTxn(Transaction t) {
        return new TrainModelRequest.TrainingTransaction(
                t.getTransactionId(),
                t.getAccountId(),
                t.getTransactionAmount(),
                t.getTransactionType(),
                t.getTransactionDate(),
                t.getLocation(),
                t.getChannel(),
                t.getCustomerAge(),
                t.getCustomerOccupation(),
                t.getLoginAttempts(),
                t.getAccountBalance()
        );
    }
}

