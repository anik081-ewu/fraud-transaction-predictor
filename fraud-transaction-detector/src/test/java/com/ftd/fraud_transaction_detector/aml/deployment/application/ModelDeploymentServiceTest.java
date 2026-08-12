package com.ftd.fraud_transaction_detector.aml.deployment.application;

import com.ftd.fraud_transaction_detector.aml.deployment.api.ModelDeploymentRequest;
import com.ftd.fraud_transaction_detector.aml.deployment.domain.ActiveModelPointer;
import com.ftd.fraud_transaction_detector.aml.deployment.infrastructure.ModelDeploymentRepository;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlModelRegistryEntry;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.AmlModelRegistryRepository;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.FileChecksumService;
import com.ftd.fraud_transaction_detector.auth.dto.UserResponse;
import com.ftd.fraud_transaction_detector.auth.service.AuthService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

class ModelDeploymentServiceTest {

    @Test
    void atomicallyPromotesValidatedCandidateForAdmin() throws Exception {
        Fixture fixture = fixture("ADMIN");
        AmlModelRegistryEntry candidate = model("HST-2", "VALIDATED");
        when(fixture.registry.findRequired("HST-2")).thenReturn(candidate);
        when(fixture.deployments.lockPointer("HALF_SPACE_TREES", "RETAIL_GENERAL"))
                .thenReturn(Optional.empty());
        when(fixture.checksums.sha256Bundle(Path.of("artifact-HST-2"))).thenReturn("a".repeat(64));

        var result = fixture.service.promote("HST-2", request(), "Bearer token");

        assertEquals("PROMOTION", result.deploymentAction());
        assertEquals("HST-2", result.activatedModelVersion());
        verify(fixture.deployments).promote(eq(candidate), eq(null), any());
    }

    @Test
    void rejectsPromotionBeforeValidation() {
        Fixture fixture = fixture("ADMIN");
        when(fixture.registry.findRequired("HST-2")).thenReturn(model("HST-2", "CANDIDATE"));

        assertThrows(IllegalStateException.class,
                () -> fixture.service.promote("HST-2", request(), "Bearer token"));

        verify(fixture.deployments, never()).promote(any(), any(), any());
    }

    @Test
    void rollsBackToPreviousCompatibleChampion() throws Exception {
        Fixture fixture = fixture("AML_ADMIN");
        AmlModelRegistryEntry current = model("HST-2", "CHAMPION");
        AmlModelRegistryEntry previous = model("HST-1", "CHALLENGER");
        when(fixture.registry.findRequired("HST-2")).thenReturn(current);
        when(fixture.registry.findRequired("HST-1")).thenReturn(previous);
        when(fixture.deployments.lockPointer("HALF_SPACE_TREES", "RETAIL_GENERAL"))
                .thenReturn(Optional.of(new ActiveModelPointer(
                        "HALF_SPACE_TREES", "RETAIL_GENERAL", "HST-2", "HST-1",
                        2, "admin", Instant.parse("2026-08-09T00:00:00Z")
                )));
        when(fixture.checksums.sha256Bundle(Path.of("artifact-HST-1"))).thenReturn("a".repeat(64));

        var result = fixture.service.rollback("HST-2", request(), "Bearer token");

        assertEquals("ROLLBACK", result.deploymentAction());
        assertEquals("HST-1", result.activatedModelVersion());
        verify(fixture.deployments).rollback(eq(current), eq(previous), any());
    }

    @Test
    void rejectsReviewerDeploymentAction() {
        Fixture fixture = fixture("REVIEWER");

        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.promote("HST-2", request(), "Bearer token"));

        verify(fixture.registry, never()).findRequired(any());
    }

    private Fixture fixture(String role) {
        AmlModelRegistryRepository registry = mock(AmlModelRegistryRepository.class);
        ModelDeploymentRepository deployments = mock(ModelDeploymentRepository.class);
        FileChecksumService checksums = mock(FileChecksumService.class);
        AuthService auth = mock(AuthService.class);
        when(auth.getCurrentUser("Bearer token"))
                .thenReturn(new UserResponse(1L, "admin", "AML Admin", role, true));
        ModelDeploymentService service = new ModelDeploymentService(
                registry, deployments, checksums, auth,
                Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC)
        );
        return new Fixture(service, registry, deployments, checksums);
    }

    private AmlModelRegistryEntry model(String version, String status) {
        return new AmlModelRegistryEntry(
                version, "HALF_SPACE_TREES", "RETAIL_GENERAL", "AML_FEATURES_V2",
                UUID.randomUUID(), "artifact-" + version, "a".repeat(64), "d".repeat(64), null,
                "s".repeat(64), status, 100L, 2_000L,
                0.02, 2_000L, 40L, 0.2, 0.7, 0.9,
                "{}", "{\"threshold\":0.8}", "trainer", Instant.parse("2026-08-05T00:00:00Z")
        );
    }

    private ModelDeploymentRequest request() {
        return new ModelDeploymentRequest(UUID.randomUUID(), "Approved after challenger validation");
    }

    private record Fixture(
            ModelDeploymentService service,
            AmlModelRegistryRepository registry,
            ModelDeploymentRepository deployments,
            FileChecksumService checksums
    ) {
    }
}
