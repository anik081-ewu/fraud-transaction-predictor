package com.ftd.fraud_transaction_detector.aml.feature.calculator;

import com.ftd.fraud_transaction_detector.aml.feature.FeatureFixtures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.FeatureContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TrustedProfileSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AmountFeatureCalculatorTest {

    private final AmountFeatureCalculator calculator = new AmountFeatureCalculator();

    @Test
    void calculatesPriorOnlyStatisticsAndRatios() {
        LocalDateTime currentTime = LocalDateTime.of(2026, 8, 4, 12, 0);
        FeatureContext context = new FeatureContext(
                FeatureFixtures.current("T4", 400, currentTime),
                3,
                FeatureFixtures.trustedProfile(3),
                List.of(
                        FeatureFixtures.history("T1", 100, currentTime.minusHours(3), "B1", true),
                        FeatureFixtures.history("T2", 200, currentTime.minusHours(2), "B2", true),
                        FeatureFixtures.history("T3", 300, currentTime.minusHours(1), "B3", true)
                )
        );

        var result = calculator.calculate(context);

        assertEquals(200.0, result.last30Average());
        assertEquals(200.0, result.last30Median());
        assertEquals(2.0, result.amountVsLast30Average());
        assertEquals(400.0 / 10_000.0, result.amountBalanceRatio());
    }

    @Test
    void leavesHistoryRatiosMissingDuringColdStart() {
        LocalDateTime currentTime = LocalDateTime.of(2026, 8, 4, 12, 0);
        FeatureContext context = new FeatureContext(
                FeatureFixtures.current("T1", 100, currentTime),
                0,
                TrustedProfileSnapshot.empty(),
                List.of()
        );

        var result = calculator.calculate(context);

        assertNull(result.last30Average());
        assertNull(result.amountVsLast30Average());
        assertNull(result.amountZScoreLast30());
    }
}
