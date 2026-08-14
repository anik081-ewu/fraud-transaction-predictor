package com.ftd.fraud_transaction_detector.fraud.service;

import com.ftd.fraud_transaction_detector.aml.learning.application.LearningEligibilityService;
import com.ftd.fraud_transaction_detector.fraud.entity.FraudAlert;
import com.ftd.fraud_transaction_detector.fraud.repo.FraudAlertRepository;
import com.ftd.fraud_transaction_detector.transactions.service.TransactionFraudLabelService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class FraudAlertReviewService {

    private final FraudAlertRepository fraudAlertRepository;
    private final LearningEligibilityService learningEligibilityService;
    private final TransactionFraudLabelService fraudLabelService;

    public FraudAlertReviewService(
            FraudAlertRepository fraudAlertRepository,
            LearningEligibilityService learningEligibilityService,
            TransactionFraudLabelService fraudLabelService
    ) {
        this.fraudAlertRepository = fraudAlertRepository;
        this.learningEligibilityService = learningEligibilityService;
        this.fraudLabelService = fraudLabelService;
    }

    @Transactional
    public FraudAlert markFalsePositive(Long alertId, String reviewedBy) {
        FraudAlert alert = getAlert(alertId);
        if ("STR_GENERATED".equals(alert.getReviewStatus())) {
            throw new IllegalStateException("An alert with generated STR cannot be marked false positive");
        }
        alert.setReviewStatus("FALSE_POSITIVE");
        alert.setReviewedBy(defaultUser(reviewedBy));
        alert.setReviewedAt(Instant.now());
        learningEligibilityService.releaseFalsePositive(alert.getTransactionId(), defaultUser(reviewedBy));
        fraudLabelService.markFalsePositive(alert.getTransactionId(), defaultUser(reviewedBy));
        return fraudAlertRepository.save(alert);
    }

    @Transactional
    public FraudAlert markStrGenerated(Long alertId, String reviewedBy, String fileName) {
        FraudAlert alert = getAlert(alertId);
        if ("FALSE_POSITIVE".equals(alert.getReviewStatus())) {
            throw new IllegalStateException("A false-positive alert cannot generate an STR");
        }
        Instant now = Instant.now();
        alert.setReviewStatus("STR_GENERATED");
        alert.setReviewedBy(defaultUser(reviewedBy));
        alert.setReviewedAt(now);
        alert.setStrFilePath(fileName);
        alert.setStrGeneratedAt(now);
        learningEligibilityService.rejectAsSuspicious(
                alert.getTransactionId(), defaultUser(reviewedBy), "STR generated after analyst review"
        );
        fraudLabelService.markStrGenerated(alert.getTransactionId(), defaultUser(reviewedBy));
        return fraudAlertRepository.save(alert);
    }

    private FraudAlert getAlert(Long alertId) {
        return fraudAlertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Fraud alert not found: " + alertId));
    }

    private static String defaultUser(String value) {
        return value == null || value.isBlank() ? "system" : value.trim();
    }
}
