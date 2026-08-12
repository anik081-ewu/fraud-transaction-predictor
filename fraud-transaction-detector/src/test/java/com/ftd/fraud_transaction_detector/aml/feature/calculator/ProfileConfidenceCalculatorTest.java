package com.ftd.fraud_transaction_detector.aml.feature.calculator;

import com.ftd.fraud_transaction_detector.aml.feature.FeatureFixtures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.FeatureContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.ProfileStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfileConfidenceCalculatorTest {

    @ParameterizedTest
    @CsvSource({
            "0,COLD_START",
            "1,LOW_CONFIDENCE",
            "9,LOW_CONFIDENCE",
            "10,DEVELOPING",
            "29,DEVELOPING",
            "30,ESTABLISHED"
    })
    void appliesColdStartStatusThresholds(long trustedCount, ProfileStatus expectedStatus) {
        FeatureContext context = new FeatureContext(
                FeatureFixtures.current("CURRENT", 100, LocalDateTime.of(2026, 8, 4, 12, 0)),
                trustedCount,
                FeatureFixtures.trustedProfile(trustedCount),
                List.of()
        );

        var result = new ProfileConfidenceCalculator().calculate(context);

        assertEquals(expectedStatus, result.status());
        assertEquals(Math.min(trustedCount / 30.0, 1.0), result.confidence());
    }
}
