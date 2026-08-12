package com.ftd.fraud_transaction_detector.aml.feature.calculator;

import com.ftd.fraud_transaction_detector.aml.feature.FeatureFixtures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.FeatureContext;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoveltyFeatureCalculatorTest {

    @Test
    void suspiciousHistoryDoesNotTeachTrustedNoveltyBaseline() {
        LocalDateTime currentTime = LocalDateTime.of(2026, 8, 4, 23, 0);
        FeatureContext context = new FeatureContext(
                FeatureFixtures.current("T3", 400, currentTime),
                2,
                FeatureFixtures.trustedProfile(30),
                List.of(
                        FeatureFixtures.history("T1", 100, currentTime.minusDays(2), "BENEFICIARY-OLD", true),
                        FeatureFixtures.history("T2", 200, currentTime.minusDays(1), "BENEFICIARY-NEW", false)
                )
        );

        var result = new NoveltyFeatureCalculator().calculate(context);

        assertTrue(result.newBeneficiary());
        assertFalse(result.newLocation());
        assertTrue(result.newChannel());
        assertTrue(result.newDevice());
        assertTrue(result.unusualTransactionHour());
    }
}
