package com.ftd.fraud_transaction_detector.aml.profile.application;

import com.ftd.fraud_transaction_detector.aml.learning.domain.LearningEligibilityDecision;
import com.ftd.fraud_transaction_detector.aml.profile.infrastructure.CustomerProfileRepository;
import com.ftd.fraud_transaction_detector.fraud.dto.FraudPredictionResponse;
import com.ftd.fraud_transaction_detector.transactions.entity.Transaction;
import org.springframework.stereotype.Service;

@Service
public class CustomerProfileService {

    private final CustomerProfileRepository profileRepository;

    public CustomerProfileService(CustomerProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    public void applyPredictionOutcome(
            Transaction transaction,
            FraudPredictionResponse response,
            LearningEligibilityDecision decision
    ) {
        boolean trusted = decision.eligibleForTrustedProfile();
        profileRepository.updateObserved(transaction);
        if (trusted) {
            profileRepository.updateTrusted(transaction);
        }
        profileRepository.saveRecent(transaction, trusted, response.riskLevel());
    }

    public void learnReviewedFalsePositive(Transaction transaction) {
        profileRepository.updateTrusted(transaction);
        profileRepository.markRecentTrusted(transaction.getTransactionId());
    }
}
