package com.ftd.fraud_transaction_detector.aml.learning.application;

import com.ftd.fraud_transaction_detector.aml.learning.domain.LearningEligibilityDecision;
import com.ftd.fraud_transaction_detector.aml.learning.domain.LearningEligibilityPolicy;
import com.ftd.fraud_transaction_detector.aml.learning.infrastructure.LearningEligibilityRepository;
import com.ftd.fraud_transaction_detector.aml.profile.application.CustomerProfileService;
import com.ftd.fraud_transaction_detector.fraud.dto.FraudPredictionResponse;
import com.ftd.fraud_transaction_detector.transactions.entity.Transaction;
import com.ftd.fraud_transaction_detector.transactions.repo.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningEligibilityService {

    private final LearningEligibilityPolicy policy;
    private final LearningEligibilityRepository repository;
    private final TransactionRepository transactionRepository;
    private final CustomerProfileService customerProfileService;

    public LearningEligibilityService(
            LearningEligibilityPolicy policy,
            LearningEligibilityRepository repository,
            TransactionRepository transactionRepository,
            CustomerProfileService customerProfileService
    ) {
        this.policy = policy;
        this.repository = repository;
        this.transactionRepository = transactionRepository;
        this.customerProfileService = customerProfileService;
    }

    public LearningEligibilityDecision evaluateAndPersist(
            Transaction transaction,
            FraudPredictionResponse response
    ) {
        LearningEligibilityDecision decision = policy.evaluate(response);
        repository.insert(transaction.getTransactionId(), decision);
        return decision;
    }

    @Transactional
    public boolean releaseFalsePositive(String transactionId, String reviewedBy) {
        boolean released = repository.releaseFalsePositive(transactionId, reviewedBy);
        if (!released) {
            return false;
        }
        Transaction transaction = transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));
        customerProfileService.learnReviewedFalsePositive(transaction);
        return true;
    }

    @Transactional
    public boolean rejectAsSuspicious(String transactionId, String reviewedBy, String reason) {
        return repository.rejectAsSuspicious(transactionId, reviewedBy, reason);
    }
}
