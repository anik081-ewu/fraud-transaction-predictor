package com.ftd.fraud_transaction_detector.aml.training.application;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerminalRiskHistoryIndexTest {

    private static final LocalDateTime AS_OF = LocalDateTime.of(2026, 8, 15, 12, 0);

    @Test
    void excludesFutureTransactionsAndLabelsInsideReportingDelay() {
        TerminalRiskHistoryIndex.Builder builder = TerminalRiskHistoryIndex.builder();
        builder.add("T-1", AS_OF.minusDays(10), BigDecimal.valueOf(100), true, "STR_GENERATED");
        builder.add("T-1", AS_OF.minusDays(2), BigDecimal.valueOf(200), true, "STR_GENERATED");
        builder.add("T-1", AS_OF.plusHours(1), BigDecimal.valueOf(300), true, "STR_GENERATED");

        TerminalRiskHistoryIndex index = builder.build();
        var context = index.context("T-1", AS_OF, 7, 0.0, 1, true);
        var zeroDelayContext = index.context("T-1", AS_OF, 0, 0.0, 1, true);

        assertEquals(2, context.thirtyDays().transactionCount());
        assertEquals(1, context.thirtyDays().confirmedFraudCount());
        assertEquals(1, context.thirtyDays().confirmedLabelCount());
        assertEquals(2, zeroDelayContext.thirtyDays().confirmedFraudCount());
    }

    @Test
    void excludesWeakNegativesFromConfirmedRateButKeepsOperationalVolume() {
        TerminalRiskHistoryIndex.Builder builder = TerminalRiskHistoryIndex.builder();
        builder.add("T-1", AS_OF.minusDays(10), BigDecimal.valueOf(100), false, "AUTO_NO_CASE");
        builder.add("T-1", AS_OF.minusDays(11), BigDecimal.valueOf(100), false, "REVIEW_FALSE_POSITIVE");

        var context = builder.build().context("T-1", AS_OF, 7, 0.0, 1, true);

        assertEquals(2, context.thirtyDays().transactionCount());
        assertEquals(1, context.thirtyDays().confirmedLabelCount());
        assertEquals(0, context.thirtyDays().confirmedFraudCount());
    }

    @Test
    void usesHalfOpenWindowBoundaries() {
        TerminalRiskHistoryIndex.Builder builder = TerminalRiskHistoryIndex.builder();
        LocalDateTime labelCutoff = AS_OF.minusDays(7);
        builder.add("T-1", labelCutoff.minusDays(30), BigDecimal.valueOf(100), true, "STR_GENERATED");
        builder.add("T-1", labelCutoff, BigDecimal.valueOf(200), true, "STR_GENERATED");
        builder.add("T-1", AS_OF.minusDays(30), BigDecimal.valueOf(300), false, "REVIEW_FALSE_POSITIVE");
        builder.add("T-1", AS_OF, BigDecimal.valueOf(400), false, "REVIEW_FALSE_POSITIVE");

        var context = builder.build().context("T-1", AS_OF, 7, 0.0, 1, true);

        assertEquals(2, context.thirtyDays().transactionCount());
        assertEquals(1, context.thirtyDays().confirmedFraudCount());
        assertEquals(2, context.thirtyDays().confirmedLabelCount());
    }
}
