package com.ftd.fraud_transaction_detector.aml.training.application;

import com.ftd.fraud_transaction_detector.aml.training.client.IncrementalTrainingClient;
import com.ftd.fraud_transaction_detector.aml.training.client.IncrementalTrainingRequest;
import com.ftd.fraud_transaction_detector.aml.training.client.IncrementalTrainingResponse;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlModelRegistryEntry;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingRun;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingType;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.AmlModelRegistryRepository;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.AmlTrainingRunRepository;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IncrementalTrainingServiceTest {

    @Test
    void trainsAndRegistersHalfSpaceTreesCandidate() {
        UUID runId = UUID.randomUUID();
        AmlTrainingRunRepository runRepository = mock(AmlTrainingRunRepository.class);
        AmlModelRegistryRepository registryRepository = mock(AmlModelRegistryRepository.class);
        AmlModelRegistryService registryService = mock(AmlModelRegistryService.class);
        IncrementalTrainingClient client = mock(IncrementalTrainingClient.class);
        AppConfigService configService = mock(AppConfigService.class);
        AmlTrainingRun run = run(runId);
        when(runRepository.findRequired(runId)).thenReturn(run);
        when(configService.getModelArtifactBasePath("outputs/model-artifacts")).thenReturn("target/model-artifacts");
        when(configService.getHstParameters()).thenReturn(Map.of("nTrees", 25));
        when(client.train(any())).thenReturn(response(runId));
        AmlModelRegistryEntry candidate = mock(AmlModelRegistryEntry.class);
        when(registryService.registerCandidate(any(), any())).thenReturn(candidate);
        IncrementalTrainingService service = new IncrementalTrainingService(
                runRepository, registryRepository, registryService, client, configService,
                Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC)
        );

        assertEquals(candidate, service.train(runId, "nightly-job"));

        ArgumentCaptor<IncrementalTrainingRequest> request = ArgumentCaptor.forClass(IncrementalTrainingRequest.class);
        verify(client).train(request.capture());
        assertTrue(request.getValue().modelVersion().startsWith("HST-RETAIL-GENERAL-20260805-"));
        assertEquals(run.datasetChecksum(), request.getValue().datasetChecksum());
        verify(registryService).registerCandidate(any(), any());
    }

    @Test
    void trainsAndRegistersOnlineOneClassSvmCandidate() {
        UUID runId = UUID.randomUUID();
        AmlTrainingRunRepository runRepository = mock(AmlTrainingRunRepository.class);
        AmlModelRegistryRepository registryRepository = mock(AmlModelRegistryRepository.class);
        AmlModelRegistryService registryService = mock(AmlModelRegistryService.class);
        IncrementalTrainingClient client = mock(IncrementalTrainingClient.class);
        AppConfigService configService = mock(AppConfigService.class);
        AmlTrainingRun run = run(runId, "ONLINE_ONE_CLASS_SVM");
        when(runRepository.findRequired(runId)).thenReturn(run);
        when(configService.getModelArtifactBasePath("outputs/model-artifacts")).thenReturn("target/model-artifacts");
        when(configService.getOnlineOneClassSvmParameters()).thenReturn(Map.of("nu", 0.05));
        when(client.train(any())).thenReturn(response(runId, "OCSVM"));
        AmlModelRegistryEntry candidate = mock(AmlModelRegistryEntry.class);
        when(registryService.registerCandidate(any(), any())).thenReturn(candidate);
        IncrementalTrainingService service = new IncrementalTrainingService(
                runRepository, registryRepository, registryService, client, configService,
                Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC)
        );

        assertEquals(candidate, service.train(runId, "nightly-job"));

        ArgumentCaptor<IncrementalTrainingRequest> request = ArgumentCaptor.forClass(IncrementalTrainingRequest.class);
        verify(client).train(request.capture());
        assertTrue(request.getValue().modelVersion().startsWith("OCSVM-RETAIL-GENERAL-20260805-"));
        assertEquals("ONLINE_ONE_CLASS_SVM", request.getValue().modelType());
        assertEquals(Map.of("nu", 0.05), request.getValue().parameters());
    }

    private IncrementalTrainingResponse response(UUID runId) {
        return response(runId, "HST");
    }

    private IncrementalTrainingResponse response(UUID runId, String prefix) {
        String version = prefix + "-RETAIL-GENERAL-20260805-" + runId.toString().substring(0, 8);
        return new IncrementalTrainingResponse(
                "CANDIDATE_READY", version, "target/model-artifacts/" + version,
                "a".repeat(64), "b".repeat(64), 100, 0.01, 100, 1,
                0.2, 0.7, 0.9, 0.9, 1000,
                Map.of("nTrees", 25), Map.of("trainingDurationMs", 1000)
        );
    }

    private AmlTrainingRun run(UUID runId) {
        return run(runId, "HALF_SPACE_TREES");
    }

    private AmlTrainingRun run(UUID runId, String modelType) {
        return new AmlTrainingRun(
                runId, AmlTrainingType.DAILY_INCREMENTAL, "AML_FEATURES_V2",
                modelType, "RETAIL_GENERAL",
                LocalDate.of(2026, 8, 4), LocalDate.of(2026, 8, 4),
                LocalDateTime.of(2026, 8, 4, 23, 59, 59),
                100L, 100L, null, "target/dataset", "d".repeat(64),
                null, null, "TRAINING", null, Instant.parse("2026-08-05T00:00:00Z"),
                null, Instant.parse("2026-08-05T00:00:00Z")
        );
    }
}
