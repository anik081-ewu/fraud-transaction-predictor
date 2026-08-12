package com.ftd.fraud_transaction_detector.aml.profile.application;

import com.ftd.fraud_transaction_detector.aml.learning.domain.LearningEligibilityDecision;
import com.ftd.fraud_transaction_detector.aml.learning.domain.LearningEligibilityStatus;
import com.ftd.fraud_transaction_detector.aml.profile.infrastructure.CustomerProfileRepository;
import com.ftd.fraud_transaction_detector.fraud.dto.FraudPredictionResponse;
import com.ftd.fraud_transaction_detector.transactions.entity.Transaction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CustomerProfileServiceTest {

    @Test
    void suspiciousTransactionUpdatesObservedButNotTrustedProfile() {
        CustomerProfileRepository repository = mock(CustomerProfileRepository.class);
        Transaction transaction = new Transaction();
        FraudPredictionResponse response = response(true, "HIGH");
        LearningEligibilityDecision decision = decision(false);

        new CustomerProfileService(repository).applyPredictionOutcome(transaction, response, decision);

        verify(repository).updateObserved(transaction);
        verify(repository, never()).updateTrusted(transaction);
        verify(repository).saveRecent(transaction, false, "HIGH");
    }

    @Test
    void acceptedTransactionUpdatesBothProfiles() {
        CustomerProfileRepository repository = mock(CustomerProfileRepository.class);
        Transaction transaction = new Transaction();
        FraudPredictionResponse response = response(false, "NORMAL");
        LearningEligibilityDecision decision = decision(true);

        new CustomerProfileService(repository).applyPredictionOutcome(transaction, response, decision);

        verify(repository).updateObserved(transaction);
        verify(repository).updateTrusted(transaction);
        verify(repository).saveRecent(transaction, true, "NORMAL");
    }

    private FraudPredictionResponse response(boolean suspicious, String riskLevel) {
        return new FraudPredictionResponse(
                "T-1", "A-1", suspicious, riskLevel, 0,
                Map.of(), Map.of(), List.of(), "ALLOW"
        );
    }

    private LearningEligibilityDecision decision(boolean trusted) {
        return new LearningEligibilityDecision(
                trusted ? LearningEligibilityStatus.LEARN_IMMEDIATELY : LearningEligibilityStatus.WAIT_FOR_REVIEW,
                "test", trusted, trusted, trusted
        );
    }
}
