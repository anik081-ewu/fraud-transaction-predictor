package com.ftd.fraud_transaction_detector.aml.feature.application;

import com.ftd.fraud_transaction_detector.aml.feature.FeatureFixtures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.FeatureContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TransactionFeatureVector;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeatureEngineeringServiceTest {

    @Test
    void t4FeatureVectorUsesOnlyT1ThroughT3() {
        LocalDateTime t4Time = LocalDateTime.of(2026, 8, 4, 12, 0);
        Instant generatedAt = Instant.parse("2026-08-04T06:00:00Z");
        FeatureContext context = new FeatureContext(
                FeatureFixtures.current("T4", 400, t4Time),
                3,
                FeatureFixtures.trustedProfile(3),
                List.of(
                        FeatureFixtures.history("T1", 100, t4Time.minusHours(3), "B1", true),
                        FeatureFixtures.history("T2", 200, t4Time.minusHours(2), "B2", true),
                        FeatureFixtures.history("T3", 300, t4Time.minusHours(1), "B3", true)
                )
        );

        TransactionFeatureVector result = new FeatureEngineeringService(
                Clock.fixed(generatedAt, ZoneOffset.UTC)
        ).calculate(context, "AML_FEATURES_V2", BigDecimal.valueOf(500));

        assertEquals("T4", result.transactionId());
        assertEquals("AML_FEATURES_V2", result.featureVersion());
        assertEquals(t4Time.toLocalDate(), result.businessDate());
        assertEquals(200.0, result.amount().last30Average());
        assertEquals(4, result.velocity().transactionCount24Hours());
        assertEquals(3, result.profile().customerHistoryCount());
        assertEquals(generatedAt, result.generatedAt());
    }
}
