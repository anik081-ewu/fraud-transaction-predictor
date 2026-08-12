package com.ftd.fraud_transaction_detector.aml.feature.calculator;

import com.ftd.fraud_transaction_detector.aml.feature.FeatureFixtures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.FeatureContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.PeerContext;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PeerFeatureCalculatorTest {

    @Test
    void calculatesPeerAndExpectedTurnoverDeviation() {
        FeatureContext context = new FeatureContext(
                FeatureFixtures.current("T1", 400, LocalDateTime.of(2026, 8, 4, 12, 0)),
                0,
                null,
                List.of(),
                new PeerContext(
                        "RETAIL_SALARIED", 100.0, 90.0, 50.0, 0.75,
                        "RETAIL", "LOW", 2_000.0
                )
        );

        var result = new PeerFeatureCalculator().calculate(context);

        assertEquals(4.0, result.amountVsPeerAverage());
        assertEquals(6.0, result.peerAmountZScore());
        assertEquals(0.2, result.amountVsExpectedTurnover());
    }

    @Test
    void preservesMissingPeerStatisticsInsteadOfUsingZero() {
        FeatureContext context = new FeatureContext(
                FeatureFixtures.current("T1", 400, LocalDateTime.of(2026, 8, 4, 12, 0)),
                0,
                null,
                List.of()
        );

        var result = new PeerFeatureCalculator().calculate(context);

        assertNull(result.amountVsPeerAverage());
        assertNull(result.peerAmountZScore());
        assertNull(result.amountVsExpectedTurnover());
    }
}
