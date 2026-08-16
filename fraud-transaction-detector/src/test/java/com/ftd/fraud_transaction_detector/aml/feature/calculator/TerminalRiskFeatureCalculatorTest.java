package com.ftd.fraud_transaction_detector.aml.feature.calculator;

import com.ftd.fraud_transaction_detector.aml.feature.domain.TerminalRiskContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TerminalWindowStatistics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalRiskFeatureCalculatorTest {

    @Test
    void bayesianSmoothingPreventsOneFraudFromProducingCertainRisk() {
        var oneFraud = new TerminalWindowStatistics(1, 500.0, 1, 1);
        var context = new TerminalRiskContext(true, oneFraud, oneFraud, oneFraud, 0.01, 20.0, 1);

        var result = new TerminalRiskFeatureCalculator().calculate(context);

        assertEquals(1.2 / 21.0, result.fraudRate1Day(), 0.000001);
        assertTrue(result.fraudRate1Day() < 0.1);
    }
}
