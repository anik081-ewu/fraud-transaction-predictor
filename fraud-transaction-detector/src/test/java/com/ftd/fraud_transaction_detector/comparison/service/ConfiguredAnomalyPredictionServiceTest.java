package com.ftd.fraud_transaction_detector.comparison.service;

import com.ftd.fraud_transaction_detector.aml.feature.FeatureFixtures;
import com.ftd.fraud_transaction_detector.aml.feature.application.FeatureEngineeringService;
import com.ftd.fraud_transaction_detector.aml.feature.domain.FeatureContext;
import com.ftd.fraud_transaction_detector.comparison.dto.ComparisonPredictResponse;
import com.ftd.fraud_transaction_detector.comparison.repo.AnomalyConfigRepository;
import com.ftd.fraud_transaction_detector.fraud.client.PersistedFeaturePredictionClient;
import com.ftd.fraud_transaction_detector.fraud.dto.FraudPredictionResponse;
import com.ftd.fraud_transaction_detector.fraud.dto.PersistedFeaturePredictRequest;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.AmlModelRegistryRepository;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlModelRegistryEntry;
import com.ftd.fraud_transaction_detector.aml.deployment.infrastructure.ModelDeploymentRepository;
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
        when(configService.isHstEnabled(true)).thenReturn(false);
        when(configService.isOnlineOneClassSvmEnabled(true)).thenReturn(false);
        when(persistedClient.predict(any())).thenReturn(new ComparisonPredictResponse(
                "T4", "ACCOUNT-1",
                Map.of(
                        "IsolationForest", Map.of("anomaly", true),
                        "LOF", Map.of("anomaly", false),
                        "OneClassSVM", Map.of("anomaly", false)
                ),
                Map.of("scoringContract", "PERSISTED_FEATURES_V2"),
                List.of()
        ));
        var service = new ConfiguredAnomalyPredictionService(
                configRepository, persistedClient, mock(AmlModelRegistryRepository.class),
                mock(ModelDeploymentRepository.class), configService
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
    void routesOnlineOneClassSvmAsShadowWithoutChangingProductionDecision() {
        AnomalyConfigRepository configRepository = mock(AnomalyConfigRepository.class);
        PersistedFeaturePredictionClient persistedClient = mock(PersistedFeaturePredictionClient.class);
        AmlModelRegistryRepository registryRepository = mock(AmlModelRegistryRepository.class);
        ModelDeploymentRepository deploymentRepository = mock(ModelDeploymentRepository.class);
        AppConfigService configService = mock(AppConfigService.class);
        AmlModelRegistryEntry onlineModel = mock(AmlModelRegistryEntry.class);
        when(configRepository.findFirstByIsActiveTrueOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());
        when(configService.getEnabledRiskPolicyModelWeights()).thenReturn(Map.of("ONLINE_ONE_CLASS_SVM", 1.0));
        when(configService.isHstEnabled(true)).thenReturn(false);
        when(configService.isOnlineOneClassSvmEnabled(true)).thenReturn(true);
        when(registryRepository.findLatestCompatible(
                "ONLINE_ONE_CLASS_SVM", "AML_FEATURES_V2", null
        )).thenReturn(Optional.of(onlineModel));
        when(onlineModel.artifactPath()).thenReturn("target/model-artifacts/OCSVM-2");
        when(onlineModel.modelVersion()).thenReturn("OCSVM-2");
        when(persistedClient.predict(any())).thenReturn(new ComparisonPredictResponse(
                "T4", "ACCOUNT-1",
                Map.of(
                        "IsolationForest", Map.of("anomaly", false),
                        "OnlineOneClassSVM", Map.of("anomaly", true, "affectsProductionDecision", false)
                ),
                Map.of("onlineOneClassSvmShadow", Map.of("modelVersion", "OCSVM-2")),
                List.of()
        ));
        var service = new ConfiguredAnomalyPredictionService(
                configRepository, persistedClient, registryRepository, deploymentRepository, configService
        );

        FraudPredictionResponse response = service.predict(vector());

        assertFalse(response.suspicious());
        assertEquals("NORMAL", response.riskLevel());
        ArgumentCaptor<PersistedFeaturePredictRequest> request = ArgumentCaptor.forClass(PersistedFeaturePredictRequest.class);
        verify(persistedClient).predict(request.capture());
        assertEquals("target/model-artifacts/OCSVM-2", request.getValue().shadowOnlineSvmDir());
        assertEquals("OCSVM-2", request.getValue().shadowOnlineSvmVersion());
    }

    @Test
    void rollbackFallbackOmitsActiveHstChampionFromProductionRequest() {
        AnomalyConfigRepository configRepository = mock(AnomalyConfigRepository.class);
        PersistedFeaturePredictionClient persistedClient = mock(PersistedFeaturePredictionClient.class);
        AmlModelRegistryRepository registryRepository = mock(AmlModelRegistryRepository.class);
        ModelDeploymentRepository deploymentRepository = mock(ModelDeploymentRepository.class);
        AppConfigService configService = mock(AppConfigService.class);
        when(configRepository.findFirstByIsActiveTrueOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());
        when(configService.getEnabledRiskPolicyModelWeights()).thenReturn(Map.of("ISOLATION_FOREST", 1.0));
        when(configService.isHstEnabled(true)).thenReturn(true);
        when(persistedClient.predict(any())).thenReturn(new ComparisonPredictResponse(
                "T4", "ACCOUNT-1", Map.of("IsolationForest", Map.of("anomaly", false)),
                Map.of(), List.of()
        ));
        var service = new ConfiguredAnomalyPredictionService(
                configRepository, persistedClient, registryRepository, deploymentRepository, configService
        );

        service.predictBatchFallback(vector());

        ArgumentCaptor<PersistedFeaturePredictRequest> request = ArgumentCaptor.forClass(PersistedFeaturePredictRequest.class);
        verify(persistedClient).predict(request.capture());
        assertEquals(null, request.getValue().activeModelsDir());
        assertEquals(null, request.getValue().activeModelVersion());
    }

    private ConfiguredAnomalyPredictionService service(
            AnomalyConfigRepository configRepository,
            PersistedFeaturePredictionClient persistedClient
    ) {
        return new ConfiguredAnomalyPredictionService(
                configRepository,
                persistedClient,
                mock(AmlModelRegistryRepository.class),
                mock(ModelDeploymentRepository.class),
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
