package com.ftd.fraud_transaction_detector.aml.validation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlModelRegistryEntry;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.AmlModelRegistryRepository;
import com.ftd.fraud_transaction_detector.aml.validation.domain.ChallengerMetrics;
import com.ftd.fraud_transaction_detector.aml.validation.infrastructure.ModelValidationRepository;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelValidationServiceTest {

    @Test
    void validatesCandidateWhenTechnicalAndReviewedGatesPass() {
        Fixture fixture = fixture(metrics(2_000, 0.02, 0.01));

        var report = fixture.service.validate("HST-1", null);

        assertEquals("PASSED", report.validationStatus());
        verify(fixture.validationRepository).markValidated("HST-1", report.metrics());
    }

    @Test
    void keepsCandidateWhenThereIsInsufficientSilentData() {
        Fixture fixture = fixture(metrics(100, 0.02, 0.01));

        var report = fixture.service.validate("HST-1", null);

        assertEquals("INSUFFICIENT_DATA", report.validationStatus());
        verify(fixture.validationRepository, never()).markValidated(any(), any());
    }

    @Test
    void failsCandidateWhenAnomalyVolumeIsUnsafe() {
        Fixture fixture = fixture(metrics(2_000, 0.25, 0.01));

        var report = fixture.service.validate("HST-1", null);

        assertEquals("FAILED", report.validationStatus());
        verify(fixture.validationRepository, never()).markValidated(any(), any());
    }

    private Fixture fixture(ChallengerMetrics metrics) {
        AmlModelRegistryRepository registryRepository = mock(AmlModelRegistryRepository.class);
        ModelValidationRepository validationRepository = mock(ModelValidationRepository.class);
        AppConfigService configService = mock(AppConfigService.class);
        when(registryRepository.findRequired("HST-1")).thenReturn(model());
        when(validationRepository.calculate(eq("HST-1"), eq(0.8), any(), any())).thenReturn(metrics);
        when(configService.getValidationMinRows()).thenReturn(1_000);
        when(configService.getValidationMinAnomalyRate()).thenReturn(0.001);
        when(configService.getValidationMaxAnomalyRate()).thenReturn(0.10);
        when(configService.getValidationMaxDailyRateStddev()).thenReturn(0.05);
        when(configService.getValidationMinReviewedAlerts()).thenReturn(20);
        when(configService.getValidationMinReviewedPrecision()).thenReturn(0.20);
        var service = new ModelValidationService(
                registryRepository, validationRepository, configService, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC)
        );
        return new Fixture(service, validationRepository);
    }

    private AmlModelRegistryEntry model() {
        return new AmlModelRegistryEntry(
                "HST-1", "HALF_SPACE_TREES", "RETAIL_GENERAL", "AML_FEATURES_V2",
                UUID.randomUUID(), "artifact", "a".repeat(64), "d".repeat(64), null,
                "s".repeat(64), "CANDIDATE", 100L, 2_000L,
                0.02, 2_000L, 40L, 0.2, 0.7, 0.9,
                "{}", "{\"threshold\":0.8}", "trainer", Instant.parse("2026-08-05T00:00:00Z")
        );
    }

    private ChallengerMetrics metrics(long rows, double anomalyRate, double dailyStddev) {
        long candidate = Math.round(rows * anomalyRate);
        long production = Math.round(rows * 0.02);
        long overlap = Math.min(candidate, production) / 2;
        return new ChallengerMetrics(
                rows, candidate, production, overlap, candidate - overlap, production - overlap,
                anomalyRate, 0.02, 0.97, 0.25, 0.20, 0.10,
                0.20, 0.70, 0.90, dailyStddev,
                0, 0, 0, null
        );
    }

    private record Fixture(
            ModelValidationService service,
            ModelValidationRepository validationRepository
    ) {}
}
