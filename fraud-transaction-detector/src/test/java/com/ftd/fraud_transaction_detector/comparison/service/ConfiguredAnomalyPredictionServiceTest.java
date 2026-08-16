package com.ftd.fraud_transaction_detector.comparison.service;

import com.ftd.fraud_transaction_detector.aml.feature.FeatureFixtures;
import com.ftd.fraud_transaction_detector.aml.feature.application.FeatureEngineeringService;
import com.ftd.fraud_transaction_detector.aml.feature.domain.FeatureContext;
import com.ftd.fraud_transaction_detector.comparison.dto.ComparisonPredictResponse;
import com.ftd.fraud_transaction_detector.comparison.repo.AnomalyConfigRepository;
import com.ftd.fraud_transaction_detector.fraud.client.PersistedFeaturePredictionClient;
import com.ftd.fraud_transaction_detector.fraud.dto.FraudPredictionResponse;
import com.ftd.fraud_transaction_detector.fraud.dto.PersistedFeaturePredictRequest;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.verify;

class ConfiguredAnomalyPredictionServiceTest {

    @Test
    void scoresPersistedFeaturesWithDefaultModels() {
        AnomalyConfigRepository configRepository = mock(AnomalyConfigRepository.class);
        PersistedFeaturePredictionClient persistedClient = mock(PersistedFeaturePredictionClient.class);
        AppConfigService configService = mock(AppConfigService.class);
        when(configRepository.findFirstByIsActiveTrueOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());
        when(configService.getEnabledRiskPolicyModelWeights()).thenReturn(Map.of("ISOLATION_FOREST", 1.0));
        when(persistedClient.predict(any())).thenReturn(new ComparisonPredictResponse(
                "T4", "ACCOUNT-1",
                Map.of(
                        "IsolationForest", Map.of("anomaly", true),
                        "BehavioralClusterOutlier", Map.of("anomaly", false),
                        "OneClassSVM", Map.of("anomaly", false)
                ),
                Map.of("scoringContract", "PERSISTED_FEATURES_V2"),
                List.of()
        ));
        var service = new ConfiguredAnomalyPredictionService(
                configRepository, persistedClient, configService
        );

        FraudPredictionResponse response = service.predict(vector());

        assertEquals("HIGH", response.riskLevel());
        assertTrue(response.suspicious());
        assertEquals("PERSISTED_FEATURES_V2", response.featureSummary().get("scoringContract"));
        ArgumentCaptor<PersistedFeaturePredictRequest> request = ArgumentCaptor.forClass(PersistedFeaturePredictRequest.class);
        verify(persistedClient).predict(request.capture());
        assertEquals(List.of("IsolationForest"), request.getValue().modelNames());
    }

    @Test
    void returnsAuditableUnavailableResultWithoutCallingLegacyV1() {
        AnomalyConfigRepository configRepository = mock(AnomalyConfigRepository.class);
        PersistedFeaturePredictionClient persistedClient = mock(PersistedFeaturePredictionClient.class);
        when(configRepository.findFirstByIsActiveTrueOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());
        when(persistedClient.predict(any())).thenReturn(new ComparisonPredictResponse(
                "T4", "ACCOUNT-1", Map.of(), Map.of(), List.of()
        ));

        FraudPredictionResponse response = service(configRepository, persistedClient).predict(vector());

        assertEquals("NORMAL", response.riskLevel());
        assertFalse(response.suspicious());
        assertEquals("ALLOW_AND_LOG", response.recommendedAction());
        assertEquals(true, response.featureSummary().get("predictionUnavailable"));
    }

    @Test
    void includesSelectedLocalOutlierFactorInProductionDecision() {
        AnomalyConfigRepository configRepository = mock(AnomalyConfigRepository.class);
        PersistedFeaturePredictionClient persistedClient = mock(PersistedFeaturePredictionClient.class);
        AppConfigService configService = mock(AppConfigService.class);
        when(configRepository.findFirstByIsActiveTrueOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());
        when(configService.getEnabledRiskPolicyModelWeights()).thenReturn(Map.of("BEHAVIORAL_CLUSTER_OUTLIER", 1.0));
        when(persistedClient.predict(any())).thenReturn(new ComparisonPredictResponse(
                "T4", "ACCOUNT-1",
                Map.of(
                        "IsolationForest", Map.of("anomaly", false),
                        "BehavioralClusterOutlier", Map.of("anomaly", true)
                ),
                Map.of(),
                List.of()
        ));
        var service = new ConfiguredAnomalyPredictionService(
                configRepository, persistedClient, configService
        );

        FraudPredictionResponse response = service.predict(vector());

        assertTrue(response.suspicious());
        assertEquals("HIGH", response.riskLevel());
        ArgumentCaptor<PersistedFeaturePredictRequest> request = ArgumentCaptor.forClass(PersistedFeaturePredictRequest.class);
        verify(persistedClient).predict(request.capture());
        assertEquals(List.of("BehavioralClusterOutlier"), request.getValue().modelNames());
    }

    private ConfiguredAnomalyPredictionService service(
            AnomalyConfigRepository configRepository,
            PersistedFeaturePredictionClient persistedClient
    ) {
        return new ConfiguredAnomalyPredictionService(
                configRepository,
                persistedClient,
                mock(AppConfigService.class)
        );
    }

    private com.ftd.fraud_transaction_detector.aml.feature.domain.TransactionFeatureVector vector() {
        LocalDateTime currentTime = LocalDateTime.of(2026, 8, 4, 12, 0);
        return new FeatureEngineeringService().calculate(
                new FeatureContext(
                        FeatureFixtures.current("T4", 400, currentTime),
                        1,
                        FeatureFixtures.trustedProfile(1),
                        List.of(FeatureFixtures.history("T3", 100, currentTime.minusHours(1), "B-1", true))
                ),
                "AML_FEATURES_V2",
                BigDecimal.valueOf(10_000)
        );
    }
}
