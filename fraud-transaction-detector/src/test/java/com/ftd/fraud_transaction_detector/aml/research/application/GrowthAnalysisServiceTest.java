package com.ftd.fraud_transaction_detector.aml.research.application;

import com.ftd.fraud_transaction_detector.aml.research.client.GrowthAnalysisClient;
import com.ftd.fraud_transaction_detector.aml.research.client.GrowthAnalysisRequest;
import com.ftd.fraud_transaction_detector.aml.research.client.GrowthAnalysisResponse;
import com.ftd.fraud_transaction_detector.aml.training.application.TrainingDatasetExportService;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingRun;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingType;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrowthAnalysisServiceTest {

    @Mock
    private TrainingDatasetExportService datasetService;
    @Mock
    private GrowthAnalysisClient client;
    @Mock
    private AppConfigService appConfigService;

    @Test
    void analyzesVerifiedSnapshotWithSafeDefaults() {
        UUID runId = UUID.randomUUID();
        when(datasetService.getRun(runId)).thenReturn(run(runId, "DATASET_READY"));
        GrowthAnalysisResponse expected = new GrowthAnalysisResponse(
                "COMPLETED", 10_000, 12, "AML_FEATURES_V1", List.of(10, 25, 50, 100),
                List.of("ISOLATION_FOREST"), Map.of(), List.of()
        );
        when(client.analyze(org.mockito.ArgumentMatchers.any())).thenReturn(expected);
        defaults();

        GrowthAnalysisResponse actual = new GrowthAnalysisService(datasetService, client, appConfigService)
                .analyze(runId, null);

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<GrowthAnalysisRequest> request = ArgumentCaptor.forClass(GrowthAnalysisRequest.class);
        verify(client).analyze(request.capture());
        assertThat(request.getValue().percentages()).containsExactly(10, 25, 50, 100);
        assertThat(request.getValue().datasetPath()).isEqualTo("outputs/dataset");
        assertThat(request.getValue().isolationForestMaximumTrainingRows()).isEqualTo(100_000);
    }

    @Test
    void rejectsRunBeforeDatasetIsReady() {
        UUID runId = UUID.randomUUID();
        when(datasetService.getRun(runId)).thenReturn(run(runId, "CREATED"));

        GrowthAnalysisService service = new GrowthAnalysisService(datasetService, client, appConfigService);

        assertThatThrownBy(() -> service.analyze(runId, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATASET_READY");
    }

    private void defaults() {
        when(appConfigService.getResearchMinimumRows()).thenReturn(200);
        when(appConfigService.getResearchHoldoutFraction()).thenReturn(0.20);
        when(appConfigService.getResearchMaximumEvaluationRows()).thenReturn(20_000);
        when(appConfigService.getResearchIsolationForestMaximumTrainingRows()).thenReturn(100_000);
        when(appConfigService.getResearchIsolationForestEstimators()).thenReturn(200);
        when(appConfigService.getResearchAutoencoderMaxTrainingRows()).thenReturn(50_000);
        when(appConfigService.getResearchRandomSeed()).thenReturn(42);
        when(appConfigService.getHstParameters()).thenReturn(Map.of());
        when(appConfigService.getOnlineOneClassSvmParameters()).thenReturn(Map.of());
    }

    private AmlTrainingRun run(UUID id, String status) {
        return new AmlTrainingRun(
                id, AmlTrainingType.BACKTEST, "AML_FEATURES_V1", "HALF_SPACE_TREES", null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                LocalDateTime.of(2026, 2, 1, 0, 0), 10_000L, 10_000L, null,
                "outputs/dataset", "checksum", null, null, status, null,
                Instant.parse("2026-02-01T00:00:00Z"), null, Instant.parse("2026-02-01T00:00:00Z")
        );
    }
}
