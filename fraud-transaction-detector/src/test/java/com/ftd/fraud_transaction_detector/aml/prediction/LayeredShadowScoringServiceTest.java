package com.ftd.fraud_transaction_detector.aml.prediction;

import com.ftd.fraud_transaction_detector.aml.behaviour.customer.CustomerBehaviourScore;
import com.ftd.fraud_transaction_detector.aml.behaviour.customer.CustomerBehaviourScorer;
import com.ftd.fraud_transaction_detector.aml.behaviour.peer.PeerBehaviourScore;
import com.ftd.fraud_transaction_detector.aml.behaviour.peer.PeerBehaviourScorer;
import com.ftd.fraud_transaction_detector.aml.feature.domain.PeerFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TransactionFeatureVector;
import com.ftd.fraud_transaction_detector.aml.risk.application.WeightedRiskAggregationEngine;
import com.ftd.fraud_transaction_detector.aml.risk.domain.RiskPolicy;
import com.ftd.fraud_transaction_detector.aml.risk.domain.RiskPolicyRepository;
import com.ftd.fraud_transaction_detector.aml.rules.domain.RuleEngineResult;
import com.ftd.fraud_transaction_detector.aml.rules.domain.RuleSeverity;
import com.ftd.fraud_transaction_detector.aml.rules.engine.DeterministicAmlRuleEngine;
import com.ftd.fraud_transaction_detector.aml.scoring.domain.NormalizedScore;
import com.ftd.fraud_transaction_detector.aml.scoring.domain.RiskBand;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import com.ftd.fraud_transaction_detector.fraud.dto.FraudPredictionResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LayeredShadowScoringServiceTest {

    private static final Instant EVALUATED_AT = Instant.parse("2026-08-06T00:00:00Z");
    private static final RiskPolicy POLICY = new RiskPolicy(
            "AML_RISK_POLICY_V3", 0.20, 0.15, 0.40, 0.25,
            0.40, 0.65, 0.80
    );

    @Test
    void persistsNormalizedLayeredComparisonWithoutReplacingLegacyDecision() {
        CustomerBehaviourScorer customerScorer = mock(CustomerBehaviourScorer.class);
        PeerBehaviourScorer peerScorer = mock(PeerBehaviourScorer.class);
        DeterministicAmlRuleEngine ruleEngine = mock(DeterministicAmlRuleEngine.class);
        RiskPolicyRepository policyRepository = mock(RiskPolicyRepository.class);
        LayeredShadowPredictionRepository repository = mock(LayeredShadowPredictionRepository.class);
        AppConfigService configService = mock(AppConfigService.class);
        TransactionFeatureVector features = features();
        when(customerScorer.score(any(), any())).thenReturn(customer(0.80));
        when(peerScorer.score(any(), any())).thenReturn(peer(0.60));
        when(ruleEngine.evaluate(any(), any())).thenReturn(rules(0.50));
        when(policyRepository.findActive("RETAIL_SALARIED")).thenReturn(POLICY);
        when(configService.getStructuringReportingThreshold(any())).thenReturn(BigDecimal.valueOf(10_000));
        when(configService.getEnabledRiskPolicyModelWeights()).thenReturn(Map.of(
                "ISOLATION_FOREST", 0.25,
                "AUTOENCODER", 0.375,
                "BEHAVIORAL_CLUSTER_OUTLIER", 0.375
        ));
        FraudPredictionResponse legacy = legacyResponse(Map.of(
                "IsolationForest", Map.of(
                        "decisionFunction", -0.40,
                        "scoreSamples", -0.55,
                        "anomaly", true
                ),
                "Autoencoder", Map.of(
                        "scoreSamples", 14.0,
                        "normalizedScore", 0.90,
                        "normalizationVersion", "AUTOENCODER_RECONSTRUCTION_MARGIN_PROXY_V1",
                        "modelVersion", "AE-7",
                        "anomaly", true
                ),
                "BehavioralClusterOutlier", Map.of(
                        "decisionFunction", -3.2,
                        "normalizedScore", 0.70,
                        "normalizationVersion", "CLUSTER_CONDITIONAL_DISTANCE_V1",
                        "modelVersion", "BCO-4",
                        "anomaly", false
                )
        ));
        LayeredShadowScoringService service = new LayeredShadowScoringService(
                customerScorer, peerScorer, ruleEngine, new WeightedRiskAggregationEngine(),
                policyRepository, repository, configService,
                Clock.fixed(EVALUATED_AT, ZoneOffset.UTC)
        );

        LayeredShadowComparison result = service.evaluateAndPersist(features, legacy);

        assertEquals(0.73, result.layeredResult().finalRiskScore(), 0.000001);
        assertEquals(RiskBand.MEDIUM, result.layeredResult().riskLevel());
        assertEquals(0.916828, result.modelScores().get("ISOLATION_FOREST").score().normalizedScore(), 0.000001);
        assertEquals("AE-7", result.modelScores().get("AUTOENCODER").modelVersion());
        assertEquals(0.90, result.modelScores().get("AUTOENCODER").score().normalizedScore());
        assertEquals(0.70, result.modelScores().get("BEHAVIORAL_CLUSTER_OUTLIER").score().normalizedScore());
        assertTrue(result.modelScores().get("AUTOENCODER").reasonCodes().contains("AUTOENCODER_HIGH_ANOMALY_SCORE"));
        assertFalse(result.suspiciousChanged());
        assertTrue(result.riskLevelChanged());
        assertTrue(result.alertOverlap());
        assertEquals(EVALUATED_AT, result.evaluatedAt());
        ArgumentCaptor<LayeredShadowComparison> captor = ArgumentCaptor.forClass(LayeredShadowComparison.class);
        verify(repository).insert(captor.capture());
        assertEquals(result, captor.getValue());
    }

    @Test
    void recordsUnavailableReasonWhenModelHasNoNormalizedScore() {
        CustomerBehaviourScorer customerScorer = mock(CustomerBehaviourScorer.class);
        PeerBehaviourScorer peerScorer = mock(PeerBehaviourScorer.class);
        DeterministicAmlRuleEngine ruleEngine = mock(DeterministicAmlRuleEngine.class);
        RiskPolicyRepository policyRepository = mock(RiskPolicyRepository.class);
        LayeredShadowPredictionRepository repository = mock(LayeredShadowPredictionRepository.class);
        AppConfigService configService = mock(AppConfigService.class);
        when(customerScorer.score(any(), any())).thenReturn(customer(0.10));
        when(peerScorer.score(any(), any())).thenReturn(peer(0.10));
        when(ruleEngine.evaluate(any(), any())).thenReturn(rules(0.10));
        when(policyRepository.findActive("RETAIL_SALARIED")).thenReturn(POLICY);
        when(configService.getStructuringReportingThreshold(any())).thenReturn(BigDecimal.valueOf(10_000));
        when(configService.getEnabledRiskPolicyModelWeights()).thenReturn(Map.of(
                "ISOLATION_FOREST", 0.25,
                "AUTOENCODER", 0.375,
                "BEHAVIORAL_CLUSTER_OUTLIER", 0.375
        ));
        LayeredShadowScoringService service = new LayeredShadowScoringService(
                customerScorer, peerScorer, ruleEngine, new WeightedRiskAggregationEngine(),
                policyRepository, repository, configService,
                Clock.fixed(EVALUATED_AT, ZoneOffset.UTC)
        );

        LayeredShadowComparison result = service.evaluateAndPersist(features(), legacyResponse(Map.of()));

        assertTrue(result.layeredResult().reasonCodes().contains("ISOLATION_FOREST_SCORE_UNAVAILABLE"));
        assertTrue(result.layeredResult().reasonCodes().contains("AUTOENCODER_SCORE_UNAVAILABLE"));
        assertTrue(result.layeredResult().reasonCodes().contains("BEHAVIORAL_CLUSTER_OUTLIER_SCORE_UNAVAILABLE"));
        assertTrue(result.suspiciousChanged());
        assertFalse(result.alertOverlap());
    }

    private TransactionFeatureVector features() {
        TransactionFeatureVector features = mock(TransactionFeatureVector.class);
        PeerFeatures peer = mock(PeerFeatures.class);
        when(features.transactionId()).thenReturn("TX-100");
        when(features.accountId()).thenReturn("AC-455");
        when(features.featureVersion()).thenReturn("AML_FEATURES_V2");
        when(features.peer()).thenReturn(peer);
        when(peer.peerGroupCode()).thenReturn("RETAIL_SALARIED");
        return features;
    }

    private FraudPredictionResponse legacyResponse(Map<String, Object> models) {
        return new FraudPredictionResponse(
                "TX-100", "AC-455", true, "HIGH", 2,
                models, Map.of(), List.of("LEGACY_MODEL_VOTE"), "HOLD_FOR_REVIEW"
        );
    }

    private CustomerBehaviourScore customer(double score) {
        return new CustomerBehaviourScore(normalized(score, "CUSTOMER_V1"), band(score), 1.0, List.of());
    }

    private PeerBehaviourScore peer(double score) {
        return new PeerBehaviourScore(
                "RETAIL_SALARIED", normalized(score, "PEER_V1"), band(score), 1.0, List.of()
        );
    }

    private RuleEngineResult rules(double score) {
        return new RuleEngineResult(
                normalized(score, "RULES_V1"), RuleSeverity.NONE, false, List.of(), List.of()
        );
    }

    private NormalizedScore normalized(double score, String version) {
        return new NormalizedScore(score * 100.0, score, version);
    }

    private RiskBand band(double score) {
        if (score >= 0.80) return RiskBand.HIGH;
        if (score >= 0.65) return RiskBand.MEDIUM;
        if (score >= 0.40) return RiskBand.LOW;
        return RiskBand.NORMAL;
    }
}
