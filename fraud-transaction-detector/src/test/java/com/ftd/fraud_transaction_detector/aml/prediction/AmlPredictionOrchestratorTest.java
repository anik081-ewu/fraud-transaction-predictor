package com.ftd.fraud_transaction_detector.aml.prediction;

import com.ftd.fraud_transaction_detector.aml.feature.domain.TransactionFeatureVector;
import com.ftd.fraud_transaction_detector.aml.risk.domain.ComponentScores;
import com.ftd.fraud_transaction_detector.aml.risk.domain.FinalRiskResult;
import com.ftd.fraud_transaction_detector.aml.scoring.domain.RiskBand;
import com.ftd.fraud_transaction_detector.comparison.service.ConfiguredAnomalyPredictionService;
import com.ftd.fraud_transaction_detector.fraud.dto.FraudPredictionResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AmlPredictionOrchestratorTest {

    @Test
    void returnsWeightedLayeredProductionResponse() {
        ConfiguredAnomalyPredictionService ml = mock(ConfiguredAnomalyPredictionService.class);
        LayeredShadowScoringService scoring = mock(LayeredShadowScoringService.class);
        TransactionFeatureVector features = mock(TransactionFeatureVector.class);
        FraudPredictionResponse raw = response(false, "NORMAL");
        when(features.transactionId()).thenReturn("TX-1");
        when(features.accountId()).thenReturn("AC-1");
        when(ml.predict(features)).thenReturn(raw);
        when(scoring.score(features, raw)).thenReturn(new FinalRiskResult(
                "POLICY-1", 0.82, RiskBand.HIGH, true, false,
                new ComponentScores(0.7, 0.6, 0.9, 0.8), List.of("HIGH_LAYERED_RISK")
        ));

        FraudPredictionResponse result = new AmlPredictionOrchestrator(ml, scoring).predict(features);

        assertEquals("HIGH", result.riskLevel());
        assertTrue(result.suspicious());
        assertEquals("HOLD_FOR_REVIEW", result.recommendedAction());
        assertEquals(List.of("HIGH_LAYERED_RISK"), result.reasons());
        assertEquals(0.82, result.featureSummary().get("finalRiskScore"));
    }

    @Test
    void fallsBackToRawMlResponseWhenLayeredScoringFails() {
        ConfiguredAnomalyPredictionService ml = mock(ConfiguredAnomalyPredictionService.class);
        LayeredShadowScoringService scoring = mock(LayeredShadowScoringService.class);
        TransactionFeatureVector features = mock(TransactionFeatureVector.class);
        FraudPredictionResponse raw = response(false, "NORMAL");
        when(features.transactionId()).thenReturn("TX-1");
        when(ml.predict(features)).thenReturn(raw);
        when(scoring.score(features, raw)).thenThrow(new IllegalStateException("policy unavailable"));

        FraudPredictionResponse result = new AmlPredictionOrchestrator(ml, scoring).predict(features);

        assertSame(raw, result);
    }

    private FraudPredictionResponse response(boolean suspicious, String risk) {
        return new FraudPredictionResponse(
                "TX-1", "AC-1", suspicious, risk, suspicious ? 1 : 0,
                Map.of(), Map.of(), List.of(), suspicious ? "HOLD_FOR_REVIEW" : "ALLOW"
        );
    }
}
