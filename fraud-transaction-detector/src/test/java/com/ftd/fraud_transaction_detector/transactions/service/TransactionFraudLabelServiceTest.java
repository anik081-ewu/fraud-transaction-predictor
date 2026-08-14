package com.ftd.fraud_transaction_detector.transactions.service;

import com.ftd.fraud_transaction_detector.transactions.entity.Transaction;
import com.ftd.fraud_transaction_detector.transactions.repo.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionFraudLabelServiceTest {

    @Test
    void apiValidLabelReturnsToPendingWhenCaseIsCreated() {
        Transaction transaction = transaction("API", false);
        TransactionFraudLabelService service = service(transaction);

        service.markCasePending("T-1");

        assertNull(transaction.getFraudLabel());
        assertNull(transaction.getLabelSource());
        assertNull(transaction.getLabeledBy());
        assertNull(transaction.getLabeledAt());
    }

    @Test
    void uploadedGroundTruthIsNotClearedWhenCaseIsCreated() {
        Transaction transaction = transaction("BULK_UPLOAD", true);
        TransactionFraudLabelService service = service(transaction);

        service.markCasePending("T-1");

        assertEquals(true, transaction.getFraudLabel());
    }

    @Test
    void reviewOutcomesSetFinalBinaryLabels() {
        Transaction transaction = transaction("API", null);
        TransactionFraudLabelService service = service(transaction);

        service.markFalsePositive("T-1", "analyst");
        assertEquals(false, transaction.getFraudLabel());
        assertEquals("FALSE_POSITIVE_REVIEW", transaction.getLabelSource());
        assertEquals("analyst", transaction.getLabeledBy());
        service.markStrGenerated("T-1", "supervisor");
        assertEquals(true, transaction.getFraudLabel());
        assertEquals("STR_GENERATED", transaction.getLabelSource());
        assertEquals("supervisor", transaction.getLabeledBy());
    }

    private TransactionFraudLabelService service(Transaction transaction) {
        TransactionRepository repository = mock(TransactionRepository.class);
        when(repository.findByTransactionId("T-1")).thenReturn(Optional.of(transaction));
        return new TransactionFraudLabelService(repository);
    }

    private Transaction transaction(String sourceType, Boolean label) {
        Transaction transaction = new Transaction();
        transaction.setTransactionId("T-1");
        transaction.setSourceType(sourceType);
        transaction.setFraudLabel(label);
        return transaction;
    }
}
