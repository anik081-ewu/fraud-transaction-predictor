package com.ftd.fraud_transaction_detector.aml.feature.calculator;

import com.ftd.fraud_transaction_detector.aml.feature.FeatureFixtures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.FeatureContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.HistoricalTransaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BehaviorFeatureCalculatorTest {

    @Test
    void summarizesLastThirtyPriorTransactions() {
        LocalDateTime currentTime = LocalDateTime.of(2026, 8, 4, 12, 0);
        FeatureContext context = new FeatureContext(
                FeatureFixtures.current("T4", 400, currentTime),
                3,
                FeatureFixtures.trustedProfile(3),
                List.of(
                        history("T1", currentTime.minusHours(3), "DEBIT", "B1", "DHAKA", "BRANCH"),
                        history("T2", currentTime.minusHours(2), "CREDIT", "B2", "DHAKA", "MOBILE"),
                        history("T3", currentTime.minusHours(1), "CASH_WITHDRAWAL", "B2", "CHITTAGONG", "ATM")
                )
        );

        var result = new BehaviorFeatureCalculator().calculate(context);

        assertEquals(1.0 / 3.0, result.last30DebitRatio());
        assertEquals(1.0 / 3.0, result.last30CreditRatio());
        assertEquals(1.0 / 3.0, result.last30CashRatio());
        assertEquals(2, result.last30UniqueBeneficiaries());
        assertEquals(2, result.last30UniqueLocations());
        assertEquals(3, result.last30UniqueChannels());
        assertEquals(60.0, result.last30AverageTimeGapMinutes());
    }

    private static HistoricalTransaction history(
            String id,
            LocalDateTime date,
            String type,
            String beneficiary,
            String location,
            String channel
    ) {
        return new HistoricalTransaction(
                id, BigDecimal.valueOf(100), date, type, channel, location,
                beneficiary, "DEVICE", true
        );
    }
}
