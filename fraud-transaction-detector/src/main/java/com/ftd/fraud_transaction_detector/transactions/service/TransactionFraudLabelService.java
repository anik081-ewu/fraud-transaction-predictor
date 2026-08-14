package com.ftd.fraud_transaction_detector.transactions.service;

import com.ftd.fraud_transaction_detector.transactions.entity.Transaction;
import com.ftd.fraud_transaction_detector.transactions.repo.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TransactionFraudLabelService {

    private final TransactionRepository transactionRepository;

    public TransactionFraudLabelService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public void markCasePending(String transactionId) {
        Transaction transaction = required(transactionId);
        if ("API".equalsIgnoreCase(transaction.getSourceType())
                && Boolean.FALSE.equals(transaction.getFraudLabel())) {
            update(transaction, null, null, null);
        }
    }

    @Transactional
    public void markFalsePositive(String transactionId, String labeledBy) {
        update(required(transactionId), false, "FALSE_POSITIVE_REVIEW", labeledBy);
    }

    @Transactional
    public void markStrGenerated(String transactionId, String labeledBy) {
        update(required(transactionId), true, "STR_GENERATED", labeledBy);
    }

    private Transaction required(String transactionId) {
        return transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));
    }

    private void update(Transaction transaction, Boolean label, String labelSource, String labeledBy) {
        Instant now = Instant.now();
        transaction.setFraudLabel(label);
        transaction.setLabelSource(labelSource);
        transaction.setLabeledBy(labeledBy);
        transaction.setLabeledAt(label == null ? null : now);
        transaction.setUpdatedAt(now);
        transactionRepository.save(transaction);
    }
}
