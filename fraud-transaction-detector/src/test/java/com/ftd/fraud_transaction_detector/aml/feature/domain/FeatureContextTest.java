package com.ftd.fraud_transaction_detector.aml.feature.domain;

import com.ftd.fraud_transaction_detector.aml.feature.FeatureFixtures;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeatureContextTest {

    private static final LocalDateTime CURRENT_TIME = LocalDateTime.of(2026, 8, 4, 12, 0);

    @Test
    void rejectsCurrentOrFutureTransactionsToPreventLeakage() {
        var current = FeatureFixtures.current("T4", 400, CURRENT_TIME);
        var currentRow = FeatureFixtures.history("T4", 400, CURRENT_TIME, "B4", true);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new FeatureContext(current, 3, TrustedProfileSnapshot.empty(), List.of(currentRow))
        );

        assertEquals(
                "Feature context may contain only transactions before the current transaction",
                error.getMessage()
        );
    }

    @Test
    void ordersPriorTransactionsNewestFirst() {
        var current = FeatureFixtures.current("T4", 400, CURRENT_TIME);
        var oldest = FeatureFixtures.history("T1", 100, CURRENT_TIME.minusHours(3), "B1", true);
        var newest = FeatureFixtures.history("T3", 300, CURRENT_TIME.minusHours(1), "B3", true);

        FeatureContext context = new FeatureContext(
                current, 2, TrustedProfileSnapshot.empty(), List.of(oldest, newest)
        );

        assertEquals(List.of("T3", "T1"), context.recentTransactions().stream()
                .map(HistoricalTransaction::transactionId)
                .toList());
    }
}
