package com.ftd.fraud_transaction_detector.aml.deployment.application;

import com.ftd.fraud_transaction_detector.aml.deployment.domain.LayeredDeploymentPointer;
import com.ftd.fraud_transaction_detector.aml.deployment.infrastructure.LayeredArchitectureDeploymentRepository;
import com.ftd.fraud_transaction_detector.aml.feature.domain.PeerFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TransactionFeatureVector;
import com.ftd.fraud_transaction_detector.aml.model.domain.MlModelScore;
import com.ftd.fraud_transaction_detector.aml.model.domain.MlModelScores;
import com.ftd.fraud_transaction_detector.aml.prediction.LayeredShadowComparison;
import com.ftd.fraud_transaction_detector.aml.risk.application.WeightedRiskAggregationEngine;
import com.ftd.fraud_transaction_detector.aml.risk.domain.FinalRiskResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LayeredProductionRoutingServiceTest {

    @Test
    void accountCanaryBucketIsStableAndBounded() {
        LayeredProductionRoutingService service = new LayeredProductionRoutingService(
                mock(LayeredArchitectureDeploymentRepository.class)
        );

        int first = service.canaryBucket("AC00455");
        int replay = service.canaryBucket("AC00455");

        assertEquals(first, replay);
        assertTrue(first >= 1 && first <= 100);
    }

    @Test
    void fallbackPointerRoutesEveryAccountToIsolationForest() {
        LayeredArchitectureDeploymentRepository repository = mock(LayeredArchitectureDeploymentRepository.class);
        LayeredProductionRoutingService service = new LayeredProductionRoutingService(repository);
        TransactionFeatureVector features = features("AC00455");
        when(repository.findPointer("RETAIL_SALARIED"))
                .thenReturn(Optional.of(pointer("ISOLATION_FOREST_FALLBACK", 0)));

        var decision = service.resolve(features);

        assertTrue(decision.isolationForestFallback());
        assertFalse(decision.layeredCanarySelected());
    }

    @Test
    void compatibilityRequiresExactPolicyAndBothModelVersions() {
        LayeredProductionRoutingService service = new LayeredProductionRoutingService(
                mock(LayeredArchitectureDeploymentRepository.class)
        );
        LayeredDeploymentPointer pointer = pointer("LAYERED_ACTIVE", 100);
        var decision = new com.ftd.fraud_transaction_detector.aml.deployment.domain.LayeredRoutingDecision(
                pointer, true, false
        );
        LayeredShadowComparison comparison = mock(LayeredShadowComparison.class);
        FinalRiskResult result = mock(FinalRiskResult.class);
        MlModelScore hst = mock(MlModelScore.class);
        MlModelScore ocsvm = mock(MlModelScore.class);
        when(result.riskPolicyVersion()).thenReturn("AML_RISK_POLICY_V2");
        when(hst.modelVersion()).thenReturn("HST-VALIDATED-1");
        when(ocsvm.modelVersion()).thenReturn("OCSVM-VALIDATED-1");
        when(comparison.layeredResult()).thenReturn(result);
        when(comparison.modelScores()).thenReturn(new MlModelScores(Map.of(
                "HALF_SPACE_TREES", hst,
                "ONLINE_ONE_CLASS_SVM", ocsvm
        )));

        assertTrue(service.compatible(decision, comparison));
        when(ocsvm.modelVersion()).thenReturn("OCSVM-CHANGED");
        assertFalse(service.compatible(decision, comparison));
    }

    private TransactionFeatureVector features(String accountId) {
        TransactionFeatureVector features = mock(TransactionFeatureVector.class);
        PeerFeatures peer = mock(PeerFeatures.class);
        when(features.accountId()).thenReturn(accountId);
        when(features.peer()).thenReturn(peer);
        when(peer.peerGroupCode()).thenReturn("RETAIL_SALARIED");
        return features;
    }

    private LayeredDeploymentPointer pointer(String mode, int canary) {
        return new LayeredDeploymentPointer(
                "RETAIL_SALARIED", mode, "AML_RISK_POLICY_V2",
                "HST-VALIDATED-1", "OCSVM-VALIDATED-1", UUID.randomUUID(),
                canary, 1, "admin", Instant.parse("2026-08-10T00:00:00Z")
        );
    }
}
