package com.ftd.fraud_transaction_detector.aml.prediction;

import com.ftd.fraud_transaction_detector.aml.deployment.domain.LayeredDeploymentPointer;
import com.ftd.fraud_transaction_detector.aml.risk.domain.ComponentScores;
import com.ftd.fraud_transaction_detector.aml.risk.domain.FinalRiskResult;
import com.ftd.fraud_transaction_detector.aml.scoring.domain.RiskBand;
import com.ftd.fraud_transaction_detector.fraud.dto.FraudPredictionResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LayeredProductionResponseFactoryTest {

    @Test
    void exposesWeightedProductionDecisionWithoutInventingVotes() {
        FraudPredictionResponse legacy = new FraudPredictionResponse(
                "TX-1", "AC-1", false, "NORMAL", 0,
                Map.of("IsolationForest", Map.of("anomaly", false)),
                Map.of("featureVersion", "AML_FEATURES_V2"), List.of("LEGACY_NORMAL"), "ALLOW"
        );
        FinalRiskResult layered = new FinalRiskResult(
                "AML_RISK_POLICY_V2", 0.82, RiskBand.HIGH, true, false,
                new ComponentScores(0.8, 0.7, 0.85, 0.9), List.of("HIGH_LAYERED_RISK")
        );
        LayeredShadowComparison comparison = mock(LayeredShadowComparison.class);
        when(comparison.layeredResult()).thenReturn(layered);
        LayeredDeploymentPointer pointer = new LayeredDeploymentPointer(
                "RETAIL_SALARIED", "LAYERED_ACTIVE", "AML_RISK_POLICY_V2",
                "HST-1", "OCSVM-1", UUID.randomUUID(), 10, 1,
                "admin", Instant.parse("2026-08-10T00:00:00Z")
        );

        FraudPredictionResponse result = new LayeredProductionResponseFactory().create(
                legacy, comparison, pointer
        );

        assertTrue(result.suspicious());
        assertEquals("HIGH", result.riskLevel());
        assertEquals(0, result.anomalyVotes());
        assertEquals("HOLD_FOR_REVIEW", result.recommendedAction());
        assertEquals("LAYERED_WEIGHTED_RISK_V2", result.featureSummary().get("productionArchitecture"));
        Map<?, ?> architecture = (Map<?, ?>) result.modelResults().get("LayeredRiskArchitecture");
        assertEquals(true, architecture.get("productionDecision"));
        assertEquals(0.82, architecture.get("finalRiskScore"));
    }
}
