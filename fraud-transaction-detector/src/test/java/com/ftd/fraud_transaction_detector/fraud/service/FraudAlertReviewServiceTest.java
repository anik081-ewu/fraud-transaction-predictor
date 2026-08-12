package com.ftd.fraud_transaction_detector.fraud.service;

import com.ftd.fraud_transaction_detector.aml.learning.application.LearningEligibilityService;
import com.ftd.fraud_transaction_detector.fraud.entity.FraudAlert;
import com.ftd.fraud_transaction_detector.fraud.repo.FraudAlertRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FraudAlertReviewServiceTest {

    @Test
    void falsePositiveReviewReleasesLearningEligibility() {
        FraudAlertRepository alertRepository = mock(FraudAlertRepository.class);
        LearningEligibilityService learningService = mock(LearningEligibilityService.class);
        FraudAlert alert = new FraudAlert();
        alert.setTransactionId("T-1");
        when(alertRepository.findById(7L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(alert)).thenReturn(alert);

        new FraudAlertReviewService(alertRepository, learningService)
                .markFalsePositive(7L, "analyst");

        verify(learningService).releaseFalsePositive("T-1", "analyst");
    }

    @Test
    void strReviewPermanentlyRejectsLearning() {
        FraudAlertRepository alertRepository = mock(FraudAlertRepository.class);
        LearningEligibilityService learningService = mock(LearningEligibilityService.class);
        FraudAlert alert = new FraudAlert();
        alert.setTransactionId("T-1");
        when(alertRepository.findById(7L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(alert)).thenReturn(alert);

        new FraudAlertReviewService(alertRepository, learningService)
                .markStrGenerated(7L, "analyst", "report.xml");

        verify(learningService).rejectAsSuspicious(
                "T-1", "analyst", "STR generated after analyst review"
        );
    }
}
