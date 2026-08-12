package com.ftd.fraud_transaction_detector.aml.prediction;

import com.ftd.fraud_transaction_detector.aml.deployment.application.LayeredProductionRoutingService;
import com.ftd.fraud_transaction_detector.aml.deployment.domain.LayeredDeploymentPointer;
import com.ftd.fraud_transaction_detector.aml.deployment.domain.LayeredRoutingDecision;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TransactionFeatureVector;
import com.ftd.fraud_transaction_detector.comparison.service.ConfiguredAnomalyPredictionService;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import com.ftd.fraud_transaction_detector.fraud.dto.FraudPredictionResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AmlPredictionOrchestratorTest {

    @Test
    void preservesLegacyProductionResponseWhileRecordingShadowResult() {
        Fixture fixture = fixture();
        FraudPredictionResponse expected = response(true, "HIGH");
        LayeredShadowComparison comparison = mock(LayeredShadowComparison.class);
        when(fixture.legacy.predict(fixture.features)).thenReturn(expected);
        when(fixture.shadow.evaluateAndPersist(fixture.features, expected)).thenReturn(comparison);

        FraudPredictionResponse actual = fixture.orchestrator.predict(fixture.features);

        assertSame(expected, actual);
        verify(fixture.shadow).evaluateAndPersist(fixture.features, expected);
    }

    @Test
    void returnsLayeredResponseOnlyForCompatibleSelectedCanary() {
        Fixture fixture = fixture();
        FraudPredictionResponse legacy = response(false, "NORMAL");
        FraudPredictionResponse layered = response(true, "HIGH");
        LayeredShadowComparison comparison = mock(LayeredShadowComparison.class);
        LayeredDeploymentPointer pointer = mock(LayeredDeploymentPointer.class);
        LayeredRoutingDecision routing = new LayeredRoutingDecision(pointer, true, false);
        when(fixture.routing.resolve(fixture.features)).thenReturn(routing);
        when(fixture.legacy.predict(fixture.features)).thenReturn(legacy);
        when(fixture.shadow.evaluateAndPersist(fixture.features, legacy)).thenReturn(comparison);
        when(fixture.routing.compatible(routing, comparison)).thenReturn(true);
        when(fixture.responseFactory.create(legacy, comparison, pointer)).thenReturn(layered);

        FraudPredictionResponse actual = fixture.orchestrator.predict(fixture.features);

        assertSame(layered, actual);
        verify(fixture.responseFactory).create(legacy, comparison, pointer);
    }

    @Test
    void shadowFailureCannotChangeLegacyProductionResponse() {
        Fixture fixture = fixture();
        FraudPredictionResponse expected = response(false, "NORMAL");
        when(fixture.features.transactionId()).thenReturn("TX-1");
        when(fixture.legacy.predict(fixture.features)).thenReturn(expected);
        doThrow(new IllegalStateException("shadow storage unavailable"))
                .when(fixture.shadow).evaluateAndPersist(fixture.features, expected);

        FraudPredictionResponse actual = fixture.orchestrator.predict(fixture.features);

        assertSame(expected, actual);
        verify(fixture.responseFactory, never()).create(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rollbackPointerForcesIsolationForestFallback() {
        Fixture fixture = fixture();
        FraudPredictionResponse fallback = response(false, "NORMAL");
        LayeredRoutingDecision routing = new LayeredRoutingDecision(mock(LayeredDeploymentPointer.class), false, true);
        when(fixture.routing.resolve(fixture.features)).thenReturn(routing);
        when(fixture.legacy.predictBatchFallback(fixture.features)).thenReturn(fallback);

        FraudPredictionResponse actual = fixture.orchestrator.predict(fixture.features);

        assertSame(fallback, actual);
        verify(fixture.legacy).predictBatchFallback(fixture.features);
        verify(fixture.legacy, never()).predict(fixture.features);
    }

    @Test
    void disabledShadowModeDoesNotInvokeLayeredScoring() {
        Fixture fixture = fixture();
        FraudPredictionResponse expected = response(false, "NORMAL");
        when(fixture.config.isLayeredShadowEnabled(false)).thenReturn(false);
        when(fixture.legacy.predict(fixture.features)).thenReturn(expected);

        fixture.orchestrator.predict(fixture.features);

        verify(fixture.shadow, never()).evaluateAndPersist(fixture.features, expected);
    }

    private Fixture fixture() {
        ConfiguredAnomalyPredictionService legacy = mock(ConfiguredAnomalyPredictionService.class);
        LayeredShadowScoringService shadow = mock(LayeredShadowScoringService.class);
        AppConfigService config = mock(AppConfigService.class);
        LayeredProductionRoutingService routing = mock(LayeredProductionRoutingService.class);
        LayeredProductionResponseFactory responseFactory = mock(LayeredProductionResponseFactory.class);
        TransactionFeatureVector features = mock(TransactionFeatureVector.class);
        when(config.isLayeredShadowEnabled(false)).thenReturn(true);
        when(config.isLegacyComparisonEnabled(true)).thenReturn(true);
        when(routing.resolve(features)).thenReturn(LayeredRoutingDecision.legacy());
        return new Fixture(
                new AmlPredictionOrchestrator(legacy, shadow, config, routing, responseFactory),
                legacy, shadow, config, routing, responseFactory, features
        );
    }

    private FraudPredictionResponse response(boolean suspicious, String risk) {
        return new FraudPredictionResponse(
                "TX-1", "AC-1", suspicious, risk, suspicious ? 1 : 0,
                Map.of(), Map.of(), List.of(), suspicious ? "HOLD_FOR_REVIEW" : "ALLOW"
        );
    }

    private record Fixture(
            AmlPredictionOrchestrator orchestrator,
            ConfiguredAnomalyPredictionService legacy,
            LayeredShadowScoringService shadow,
            AppConfigService config,
            LayeredProductionRoutingService routing,
            LayeredProductionResponseFactory responseFactory,
            TransactionFeatureVector features
    ) {
    }
}
