package com.ftd.fraud_transaction_detector.aml.feature.calculator;

import com.ftd.fraud_transaction_detector.aml.feature.FeatureFixtures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.FeatureContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VelocityFeatureCalculatorTest {

    @Test
    void includesCurrentTransactionAndUsesOnlyRowsInsideEachWindow() {
        LocalDateTime currentTime = LocalDateTime.of(2026, 8, 4, 12, 0);
        FeatureContext context = new FeatureContext(
                FeatureFixtures.current("T4", 400, currentTime),
                3,
                FeatureFixtures.trustedProfile(3),
                List.of(
                        FeatureFixtures.history("T1", 100, currentTime.minusDays(2), "B1", true),
                        FeatureFixtures.history("T2", 200, currentTime.minusMinutes(30), "B2", true),
                        FeatureFixtures.history("T3", 400, currentTime.minusMinutes(5), "B2", true)
                )
        );

        var result = new VelocityFeatureCalculator().calculate(context, BigDecimal.valueOf(500));

        assertEquals(2, result.transactionCount10Minutes());
        assertEquals(3, result.transactionCount1Hour());
        assertEquals(3, result.transactionCount24Hours());
        assertEquals(1_100.0, result.amountSum7Days());
        assertEquals(2, result.uniqueBeneficiaries1Hour());
        assertEquals(2, result.repeatedAmountCount24Hours());
        assertEquals(1_000.0, result.belowThresholdAmountSum24Hours());
    }
}
