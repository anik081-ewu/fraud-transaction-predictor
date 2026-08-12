package com.ftd.fraud_transaction_detector.aml.learning.application;

import com.ftd.fraud_transaction_detector.aml.learning.domain.LearningEligibilityPolicy;
import com.ftd.fraud_transaction_detector.aml.learning.infrastructure.LearningEligibilityRepository;
import com.ftd.fraud_transaction_detector.aml.profile.application.CustomerProfileService;
import com.ftd.fraud_transaction_detector.transactions.entity.Transaction;
import com.ftd.fraud_transaction_detector.transactions.repo.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningEligibilityServiceTest {

    @Test
    void releasesFalsePositiveExactlyWhenAtomicTransitionSucceeds() {
        LearningEligibilityRepository repository = mock(LearningEligibilityRepository.class);
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        CustomerProfileService profileService = mock(CustomerProfileService.class);
        Transaction transaction = new Transaction();
        when(repository.releaseFalsePositive("T-1", "analyst")).thenReturn(true);
        when(transactionRepository.findByTransactionId("T-1")).thenReturn(Optional.of(transaction));
        LearningEligibilityService service = service(repository, transactionRepository, profileService);

        assertTrue(service.releaseFalsePositive("T-1", "analyst"));
        verify(profileService).learnReviewedFalsePositive(transaction);
    }

    @Test
    void repeatedFalsePositiveReviewDoesNotLearnTwice() {
        LearningEligibilityRepository repository = mock(LearningEligibilityRepository.class);
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        CustomerProfileService profileService = mock(CustomerProfileService.class);
        when(repository.releaseFalsePositive("T-1", "analyst")).thenReturn(false);
        LearningEligibilityService service = service(repository, transactionRepository, profileService);

        assertFalse(service.releaseFalsePositive("T-1", "analyst"));
        verify(transactionRepository, never()).findByTransactionId("T-1");
        verify(profileService, never()).learnReviewedFalsePositive(org.mockito.ArgumentMatchers.any());
    }

    private LearningEligibilityService service(
            LearningEligibilityRepository repository,
            TransactionRepository transactionRepository,
            CustomerProfileService profileService
    ) {
        return new LearningEligibilityService(
                new LearningEligibilityPolicy(), repository, transactionRepository, profileService
        );
    }
}
