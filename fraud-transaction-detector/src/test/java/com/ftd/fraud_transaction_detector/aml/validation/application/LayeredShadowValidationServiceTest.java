package com.ftd.fraud_transaction_detector.aml.validation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.aml.risk.domain.RiskPolicy;
import com.ftd.fraud_transaction_detector.aml.risk.domain.RiskPolicyRepository;
import com.ftd.fraud_transaction_detector.aml.validation.api.SyntheticScenarioLabelRequest;
import com.ftd.fraud_transaction_detector.aml.validation.api.ValidateLayeredShadowRequest;
import com.ftd.fraud_transaction_detector.aml.validation.domain.LayeredShadowValidationMetrics;
import com.ftd.fraud_transaction_detector.aml.validation.domain.LayeredShadowValidationReport;
import com.ftd.fraud_transaction_detector.aml.validation.domain.SegmentShadowMetrics;
import com.ftd.fraud_transaction_detector.aml.validation.domain.SyntheticScenarioLabel;
import com.ftd.fraud_transaction_detector.aml.validation.domain.SyntheticScenarioMetrics;
import com.ftd.fraud_transaction_detector.aml.validation.infrastructure.LayeredShadowValidationRepository;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LayeredShadowValidationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-06T00:00:00Z");
    private final LayeredShadowValidationRepository repository = mock(LayeredShadowValidationRepository.class);
    private final RiskPolicyRepository policyRepository = mock(RiskPolicyRepository.class);
    private final AppConfigService configService = mock(AppConfigService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private LayeredShadowValidationService service;

    @BeforeEach
    void setUp() {
        when(policyRepository.findActive(null)).thenReturn(new RiskPolicy(
                "AML_RISK_POLICY_V2", 0.20, 0.15, 0.00, 0.25, 0.15, 0.25,
                0.40, 0.65, 0.80
        ));
        when(configService.getLayeredValidationMinRows()).thenReturn(1_000);
        when(configService.getLayeredValidationMinObservationDays()).thenReturn(7);
        when(configService.getLayeredValidationMinLegacyAlerts()).thenReturn(20);
        when(configService.getLayeredValidationMaxAlertRate()).thenReturn(0.20);
        when(configService.getLayeredValidationMaxAlertVolumeIncrease()).thenReturn(0.50);
        when(configService.getLayeredValidationMinTopRiskOverlap()).thenReturn(0.50);
        when(configService.getLayeredValidationMaxDailyRateStddev()).thenReturn(0.05);
        when(configService.getLayeredValidationMaxSegmentDailyStddev()).thenReturn(0.08);
        when(configService.getLayeredValidationMinSyntheticScenarios()).thenReturn(20);
        when(configService.getLayeredValidationMinSyntheticRecall()).thenReturn(0.80);
        when(configService.getLayeredValidationMinReviewedAlerts()).thenReturn(20);
        when(configService.getLayeredValidationMaxReviewedFalsePositiveRate()).thenReturn(0.80);
        when(configService.getLayeredValidationMaxP95LatencyMs()).thenReturn(250.0);
        when(configService.getLayeredValidationMinModelAvailability()).thenReturn(0.99);
        when(configService.getLayeredValidationMaxAverageIncrementalUpdateMs()).thenReturn(3_600_000.0);
        service = new LayeredShadowValidationService(
                repository, policyRepository, configService, objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void passesOnlyWhenEveryPromotionGateHasEnoughEvidence() {
        when(repository.calculate(any(), any(), any(), any())).thenReturn(metrics(1_000, 0.50));

        LayeredShadowValidationReport report = service.validate(null);

        assertEquals("PASSED", report.validationStatus());
        assertTrue(report.blockingReasons().isEmpty());
        assertEquals("AML_RISK_POLICY_V2", report.riskPolicyVersion());
        assertEquals(NOW.minusSeconds(30L * 24 * 60 * 60), report.windowStartedAt());
        assertEquals(2, report.warnings().size());
        verify(repository).save(any(), any(), any(), any());
    }

    @Test
    void blocksPromotionWhenEvidenceVolumeIsInsufficient() {
        when(repository.calculate(any(), any(), any(), any())).thenReturn(metrics(100, 0.50));

        LayeredShadowValidationReport report = service.validate(new ValidateLayeredShadowRequest(
                null, null, NOW.minusSeconds(86_400), NOW, "risk-reviewer"
        ));

        assertEquals("INSUFFICIENT_DATA", report.validationStatus());
        assertTrue(report.blockingReasons().stream().anyMatch(reason -> reason.contains("shadow predictions")));
        assertTrue(report.blockingReasons().stream().anyMatch(reason -> reason.contains("observation days")));
        assertEquals("risk-reviewer", report.validatedBy());
    }

    @Test
    void failsWhenReviewedFalsePositiveRateBreachesPolicy() {
        when(repository.calculate(any(), any(), any(), any())).thenReturn(metrics(1_000, 0.90));

        LayeredShadowValidationReport report = service.validate(null);

        assertEquals("FAILED", report.validationStatus());
        assertTrue(report.blockingReasons().contains(
                "Reviewed false-positive rate exceeds the configured maximum"
        ));
    }

    @Test
    void labelsOnlyTransactionsThatHaveShadowEvidence() {
        SyntheticScenarioLabelRequest request = new SyntheticScenarioLabelRequest(
                "TX-1", "structuring", true, "scenario-runner"
        );
        when(repository.shadowPredictionExists("TX-1")).thenReturn(true);
        when(repository.saveScenarioLabel(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SyntheticScenarioLabel label = service.label(request);

        assertEquals("STRUCTURING", label.scenarioCode());
        assertEquals(NOW, label.createdAt());
        verify(repository).saveScenarioLabel(any());
    }

    @Test
    void rejectsScenarioLabelWithoutShadowPrediction() {
        SyntheticScenarioLabelRequest request = new SyntheticScenarioLabelRequest(
                "TX-MISSING", "structuring", true, "scenario-runner"
        );
        when(repository.shadowPredictionExists("TX-MISSING")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.label(request));
        verify(repository, never()).saveScenarioLabel(any());
    }

    @Test
    void rejectsFutureValidationWindow() {
        ValidateLayeredShadowRequest request = new ValidateLayeredShadowRequest(
                null, null, NOW, NOW.plusSeconds(1), "risk-reviewer"
        );

        assertThrows(IllegalArgumentException.class, () -> service.validate(request));
        verify(repository, never()).calculate(any(), any(), any(), any());
    }

    @Test
    void rejectsOutOfRangeValidationConfiguration() {
        when(repository.calculate(any(), any(), any(), any())).thenReturn(metrics(1_000, 0.50));
        when(configService.getLayeredValidationMinSyntheticRecall()).thenReturn(1.10);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.validate(null));

        assertTrue(exception.getMessage().contains("minSyntheticRecall"));
        verify(repository, never()).save(any(), any(), any(), any());
    }

    private LayeredShadowValidationMetrics metrics(long sampleCount, double falsePositiveRate) {
        int days = sampleCount >= 1_000 ? 7 : 1;
        long legacyAlerts = sampleCount >= 1_000 ? 100 : 5;
        long layeredAlerts = sampleCount >= 1_000 ? 110 : 5;
        long overlap = sampleCount >= 1_000 ? 80 : 3;
        return new LayeredShadowValidationMetrics(
                sampleCount, days, legacyAlerts, layeredAlerts, overlap,
                layeredAlerts - overlap, legacyAlerts - overlap,
                (double) legacyAlerts / sampleCount, (double) layeredAlerts / sampleCount,
                legacyAlerts == 0 ? null : (double) (layeredAlerts - legacyAlerts) / legacyAlerts,
                0.95, 0.62, 10, 8, 0.80,
                0.35, 0.15, 0.30, 0.65, 0.90, 0.02,
                List.of(new SegmentShadowMetrics("RETAIL_SALARIED", sampleCount, layeredAlerts,
                        (double) layeredAlerts / sampleCount, 0.03)),
                0.03, 20, 18, 0.90,
                List.of(new SyntheticScenarioMetrics("STRUCTURING", 20, 18, 0.90)),
                20, 10, 10, 0.50, falsePositiveRate,
                15.0, 100.0, 2, 1_000.0, 1_500.0, 1.0, 1.0,
                "HST-VALIDATED-1", 1, "OCSVM-VALIDATED-1", 1
        );
    }
}
