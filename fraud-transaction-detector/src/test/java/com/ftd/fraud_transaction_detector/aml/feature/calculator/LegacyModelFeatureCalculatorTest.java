package com.ftd.fraud_transaction_detector.aml.feature.calculator;

import com.ftd.fraud_transaction_detector.aml.feature.FeatureFixtures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.FeatureContext;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyModelFeatureCalculatorTest {

    @Test
    void buildsLegacyModelInputsFromPointInTimeContext() {
        LocalDateTime currentTime = LocalDateTime.of(2026, 8, 4, 12, 0);
        var context = new FeatureContext(
                FeatureFixtures.current("T4", 400, currentTime),
                3,
                FeatureFixtures.trustedProfile(3),
                List.of(FeatureFixtures.history(
                        "T3", 100, currentTime.minusHours(2), "B-1", true
                ))
        );

        var features = new LegacyModelFeatureCalculator().calculate(context);

        assertEquals(2.0, features.get("time_diff_hours"));
        assertEquals(4.0, features.get("amount_vs_user_avg"));
        assertEquals(1.0, features.get("TransactionType_DEBIT"));
        assertEquals(1.0, features.get("CustomerOccupation_SALARIED"));
        assertTrue(features.keySet().stream().anyMatch(name -> name.matches("LocationHashBucket_\\d{3}")));
        assertFalse(features.keySet().stream().anyMatch(name -> name.startsWith("Location_")));
    }
}
