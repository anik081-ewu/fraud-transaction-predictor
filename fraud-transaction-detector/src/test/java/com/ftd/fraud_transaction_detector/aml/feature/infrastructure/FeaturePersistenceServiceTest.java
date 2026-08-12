package com.ftd.fraud_transaction_detector.aml.feature.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.aml.feature.FeatureFixtures;
import com.ftd.fraud_transaction_detector.aml.feature.application.FeatureEngineeringService;
import com.ftd.fraud_transaction_detector.aml.feature.application.FeatureVersionProvider;
import com.ftd.fraud_transaction_detector.aml.feature.domain.FeatureContext;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeaturePersistenceServiceTest {

    @Test
    void writesImmutableFeatureVectorUsingItsTransactionAndFeatureVersion() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        FeatureVersionProvider versionProvider = mock(FeatureVersionProvider.class);
        when(versionProvider.generatorVersion()).thenReturn("test-generator");
        LocalDateTime currentTime = LocalDateTime.of(2026, 8, 4, 12, 0);
        var vector = new FeatureEngineeringService(
                Clock.fixed(Instant.parse("2026-08-04T06:00:00Z"), ZoneOffset.UTC)
        ).calculate(
                new FeatureContext(
                        FeatureFixtures.current("T4", 400, currentTime),
                        1,
                        FeatureFixtures.trustedProfile(1),
                        List.of(FeatureFixtures.history("T3", 300, currentTime.minusHours(1), "B3", true))
                ),
                "AML_FEATURES_V2",
                BigDecimal.valueOf(10_000)
        );

        new FeaturePersistenceService(jdbcTemplate, versionProvider, new ObjectMapper()).save(vector);

        verify(jdbcTemplate).update(anyString(), argThat((SqlParameterSource parameters) -> {
            assertEquals("T4", parameters.getValue("transactionId"));
            assertEquals("AML_FEATURES_V2", parameters.getValue("featureVersion"));
            assertEquals("test-generator", parameters.getValue("generatorVersion"));
            assertEquals("LEGACY_MODEL_INPUT_V1", parameters.getValue("modelFeatureSchema"));
            assertEquals(Timestamp.from(Instant.parse("2026-08-04T06:00:00Z")), parameters.getValue("generatedAt"));
            assertEquals(Types.TIMESTAMP, parameters.getSqlType("generatedAt"));
            assertEquals(Types.DOUBLE, parameters.getSqlType("peerAverage"));
            return true;
        }));
    }
}
