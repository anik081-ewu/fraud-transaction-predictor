package com.ftd.fraud_transaction_detector.aml.deployment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.aml.deployment.api.PromoteLayeredArchitectureRequest;
import com.ftd.fraud_transaction_detector.aml.deployment.api.RollbackLayeredArchitectureRequest;
import com.ftd.fraud_transaction_detector.aml.deployment.domain.LayeredDeploymentPointer;
import com.ftd.fraud_transaction_detector.aml.deployment.domain.LayeredDeploymentEvent;
import com.ftd.fraud_transaction_detector.aml.deployment.infrastructure.LayeredArchitectureDeploymentRepository;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlModelRegistryEntry;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.AmlModelRegistryRepository;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.FileChecksumService;
import com.ftd.fraud_transaction_detector.aml.validation.domain.LayeredShadowValidationMetrics;
import com.ftd.fraud_transaction_detector.aml.validation.domain.SegmentShadowMetrics;
import com.ftd.fraud_transaction_detector.aml.validation.domain.SyntheticScenarioMetrics;
import com.ftd.fraud_transaction_detector.aml.validation.infrastructure.LayeredShadowValidationRepository;
import com.ftd.fraud_transaction_detector.auth.dto.UserResponse;
import com.ftd.fraud_transaction_detector.auth.service.AuthService;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LayeredArchitectureDeploymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    @Test
    void promotesExactValidatedVersionsToTenPercentCanary() throws Exception {
        Fixture fixture = fixture("ADMIN");
        UUID validationId = UUID.randomUUID();
        stubPassingValidation(fixture, validationId, NOW.minusSeconds(3_600));
        stubModels(fixture);
        when(fixture.deployments.lockPointer("RETAIL_SALARIED")).thenReturn(Optional.empty());
        PromoteLayeredArchitectureRequest request = promotion(validationId, 10);

        var event = fixture.service.promote(request, "Bearer token");

        assertEquals("PROMOTION", event.deploymentAction());
        assertEquals(10, event.activatedCanaryPercentage());
        assertEquals("HST-VALIDATED-1", event.hstModelVersion());
        verify(fixture.deployments).promote(any(), eq(null), eq(event));
    }

    @Test
    void expandsCanaryOnlyWithSameValidationAndHigherPercentage() throws Exception {
        Fixture fixture = fixture("AML_ADMIN");
        UUID validationId = UUID.randomUUID();
        stubPassingValidation(fixture, validationId, NOW.minusSeconds(3_600));
        stubModels(fixture);
        LayeredDeploymentPointer current = pointer(validationId, "LAYERED_ACTIVE", 10, 1);
        when(fixture.deployments.lockPointer("RETAIL_SALARIED")).thenReturn(Optional.of(current));

        var event = fixture.service.promote(promotion(validationId, 25), "Bearer token");

        assertEquals("CANARY_EXPANSION", event.deploymentAction());
        assertEquals(25, event.activatedCanaryPercentage());
    }

    @Test
    void rejectsStalePassingValidation() throws Exception {
        Fixture fixture = fixture("ADMIN");
        UUID validationId = UUID.randomUUID();
        stubPassingValidation(fixture, validationId, NOW.minusSeconds(8L * 86_400));

        assertThrows(IllegalStateException.class,
                () -> fixture.service.promote(promotion(validationId, 10), "Bearer token"));

        verify(fixture.registry, never()).findRequired(any());
        verify(fixture.deployments, never()).promote(any(), any(), any());
    }

    @Test
    void rollsBackImmediatelyToIsolationForestFallback() {
        Fixture fixture = fixture("ADMIN");
        UUID validationId = UUID.randomUUID();
        LayeredDeploymentPointer current = pointer(validationId, "LAYERED_ACTIVE", 25, 3);
        when(fixture.deployments.lockPointer("RETAIL_SALARIED")).thenReturn(Optional.of(current));
        RollbackLayeredArchitectureRequest request = new RollbackLayeredArchitectureRequest(
                UUID.randomUUID(), "RETAIL_SALARIED", "Latency health gate breached"
        );

        var event = fixture.service.rollback(request, "Bearer token");

        assertEquals("ROLLBACK", event.deploymentAction());
        assertEquals("ISOLATION_FOREST_FALLBACK", event.activatedMode());
        assertEquals(0, event.activatedCanaryPercentage());
        verify(fixture.deployments).rollback(eq(current), any(), eq(event));
    }

    @Test
    void rejectsUnauthorizedReviewer() {
        Fixture fixture = fixture("REVIEWER");

        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.promote(promotion(UUID.randomUUID(), 10), "Bearer token"));

        verify(fixture.validations, never()).findStored(any());
    }

    @Test
    void rejectsActionIdReusedAcrossPromotionAndRollback() {
        Fixture fixture = fixture("ADMIN");
        UUID actionId = UUID.randomUUID();
        UUID validationId = UUID.randomUUID();
        LayeredDeploymentEvent rollback = new LayeredDeploymentEvent(
                UUID.randomUUID(), actionId, "ROLLBACK", "RETAIL_SALARIED",
                "LAYERED_ACTIVE", "ISOLATION_FOREST_FALLBACK", "AML_RISK_POLICY_V2",
                "HST-VALIDATED-1", "OCSVM-VALIDATED-1", validationId,
                10, 0, "rollback", "admin", NOW
        );
        when(fixture.deployments.findEvent(actionId)).thenReturn(Optional.of(rollback));
        PromoteLayeredArchitectureRequest request = new PromoteLayeredArchitectureRequest(
                actionId, validationId, "RETAIL_SALARIED", 10, "promotion"
        );

        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.promote(request, "Bearer token"));

        verify(fixture.validations, never()).findStored(any());
    }

    private Fixture fixture(String role) {
        LayeredArchitectureDeploymentRepository deployments = mock(LayeredArchitectureDeploymentRepository.class);
        LayeredShadowValidationRepository validations = mock(LayeredShadowValidationRepository.class);
        AmlModelRegistryRepository registry = mock(AmlModelRegistryRepository.class);
        FileChecksumService checksums = mock(FileChecksumService.class);
        AuthService auth = mock(AuthService.class);
        AppConfigService config = mock(AppConfigService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        when(auth.getCurrentUser("Bearer token"))
                .thenReturn(new UserResponse(1L, "admin", "AML Admin", role, true));
        when(config.isLayeredShadowEnabled(false)).thenReturn(true);
        when(config.isLegacyComparisonEnabled(false)).thenReturn(true);
        when(config.getLayeredDeploymentMaxValidationAgeDays()).thenReturn(7);
        LayeredArchitectureDeploymentService service = new LayeredArchitectureDeploymentService(
                deployments, validations, registry, checksums, auth, config, objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        return new Fixture(service, deployments, validations, registry, checksums, objectMapper);
    }

    private void stubPassingValidation(Fixture fixture, UUID validationId, Instant validatedAt) throws Exception {
        String metricsJson = fixture.objectMapper.writeValueAsString(metrics());
        when(fixture.validations.findStored(validationId)).thenReturn(Optional.of(
                new LayeredShadowValidationRepository.StoredValidation(
                        validationId, "AML_RISK_POLICY_V2", "RETAIL_SALARIED",
                        NOW.minusSeconds(7L * 86_400), NOW.minusSeconds(60), "PASSED",
                        "[]", "[]", metricsJson, "risk-reviewer", validatedAt
                )
        ));
    }

    private void stubModels(Fixture fixture) throws Exception {
        AmlModelRegistryEntry hst = model("HST-VALIDATED-1", "HALF_SPACE_TREES");
        AmlModelRegistryEntry ocsvm = model("OCSVM-VALIDATED-1", "ONLINE_ONE_CLASS_SVM");
        when(fixture.registry.findRequired(hst.modelVersion())).thenReturn(hst);
        when(fixture.registry.findRequired(ocsvm.modelVersion())).thenReturn(ocsvm);
        when(fixture.checksums.sha256Bundle(Path.of(hst.artifactPath()))).thenReturn(hst.artifactChecksum());
        when(fixture.checksums.sha256Bundle(Path.of(ocsvm.artifactPath()))).thenReturn(ocsvm.artifactChecksum());
    }

    private AmlModelRegistryEntry model(String version, String type) {
        return new AmlModelRegistryEntry(
                version, type, "RETAIL_SALARIED", "AML_FEATURES_V2",
                UUID.randomUUID(), "artifact-" + version, "a".repeat(64), "d".repeat(64), null,
                "s".repeat(64), "CANDIDATE", 100L, 2_000L,
                0.02, 2_000L, 40L, 0.2, 0.7, 0.9,
                "{}", "{}", "trainer", NOW.minusSeconds(86_400)
        );
    }

    private LayeredDeploymentPointer pointer(UUID validationId, String mode, int canary, long version) {
        return new LayeredDeploymentPointer(
                "RETAIL_SALARIED", mode, "AML_RISK_POLICY_V2",
                "HST-VALIDATED-1", "OCSVM-VALIDATED-1", validationId,
                canary, version, "admin", NOW.minusSeconds(60)
        );
    }

    private PromoteLayeredArchitectureRequest promotion(UUID validationId, int canary) {
        return new PromoteLayeredArchitectureRequest(
                UUID.randomUUID(), validationId, "RETAIL_SALARIED", canary,
                "Approved controlled production canary"
        );
    }

    private LayeredShadowValidationMetrics metrics() {
        return new LayeredShadowValidationMetrics(
                1_000, 7, 100, 110, 80, 30, 20,
                0.10, 0.11, 0.10, 0.95, 0.62,
                10, 8, 0.80, 0.35, 0.15, 0.30, 0.65, 0.90, 0.02,
                List.of(new SegmentShadowMetrics("RETAIL_SALARIED", 1_000, 110, 0.11, 0.03)),
                0.03, 20, 18, 0.90,
                List.of(new SyntheticScenarioMetrics("STRUCTURING", 20, 18, 0.90)),
                20, 10, 10, 0.50, 0.50,
                15.0, 100.0, 2, 1_000.0, 1_500.0, 1.0, 1.0,
                "HST-VALIDATED-1", 1, "OCSVM-VALIDATED-1", 1
        );
    }

    private record Fixture(
            LayeredArchitectureDeploymentService service,
            LayeredArchitectureDeploymentRepository deployments,
            LayeredShadowValidationRepository validations,
            AmlModelRegistryRepository registry,
            FileChecksumService checksums,
            ObjectMapper objectMapper
    ) {
    }
}
