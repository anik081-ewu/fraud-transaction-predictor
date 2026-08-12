package com.ftd.fraud_transaction_detector.aml.training.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.aml.training.api.RegisterCandidateModelRequest;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlModelRegistryEntry;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingRun;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingType;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.AmlModelRegistryRepository;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.AmlTrainingRunRepository;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.FileChecksumService;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AmlModelRegistryServiceTest {

    @Test
    void verifiesAndRegistersImmutableCandidateBundle() throws Exception {
        UUID runId = UUID.randomUUID();
        Path artifactBase = Path.of("target", "test-output", "model-registry-" + runId).toAbsolutePath();
        Path artifactBundle = artifactBase.resolve("HST-RETAIL-20260804-01");
        Files.createDirectories(artifactBundle);
        Files.writeString(artifactBundle.resolve("model.bin"), "candidate-model-state");
        writeManifest(artifactBundle, runId);
        FileChecksumService checksumService = new FileChecksumService();
        String checksum = checksumService.sha256Bundle(artifactBundle);

        AmlTrainingRunRepository runRepository = mock(AmlTrainingRunRepository.class);
        AmlModelRegistryRepository registryRepository = mock(AmlModelRegistryRepository.class);
        AppConfigService configService = mock(AppConfigService.class);
        AmlTrainingRun run = run(runId, "TRAINING", 250L);
        when(runRepository.findRequired(runId)).thenReturn(run);
        when(runRepository.completeCandidate(runId, "HST-RETAIL-20260804-01", 240L)).thenReturn(true);
        when(configService.getModelArtifactBasePath("outputs/model-artifacts")).thenReturn(artifactBase.toString());
        AtomicReference<AmlModelRegistryEntry> inserted = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            inserted.set(invocation.getArgument(0));
            return null;
        }).when(registryRepository).insertCandidate(any());
        when(registryRepository.findRequired("HST-RETAIL-20260804-01")).thenAnswer(invocation -> inserted.get());
        AmlModelRegistryService service = new AmlModelRegistryService(
                runRepository, registryRepository, checksumService, configService, new ObjectMapper()
        );

        AmlModelRegistryEntry result = service.registerCandidate(runId, request(checksum));

        assertEquals("CANDIDATE", result.status());
        assertEquals(run.datasetChecksum(), result.datasetChecksum());
        assertEquals(240L, result.learnedRowCount());
        assertEquals(checksum, result.artifactChecksum());
        verify(runRepository).completeCandidate(runId, result.modelVersion(), 240L);
    }

    @Test
    void rejectsArtifactWhenChecksumDoesNotMatch() throws Exception {
        UUID runId = UUID.randomUUID();
        Path artifactBase = Path.of("target", "test-output", "model-registry-invalid-" + runId).toAbsolutePath();
        Path artifactBundle = artifactBase.resolve("HST-RETAIL-20260804-01");
        Files.createDirectories(artifactBundle);
        Files.writeString(artifactBundle.resolve("model.bin"), "candidate-model-state");
        writeManifest(artifactBundle, runId);
        AmlTrainingRunRepository runRepository = mock(AmlTrainingRunRepository.class);
        AmlModelRegistryRepository registryRepository = mock(AmlModelRegistryRepository.class);
        AppConfigService configService = mock(AppConfigService.class);
        when(runRepository.findRequired(runId)).thenReturn(run(runId, "TRAINING", 250L));
        when(configService.getModelArtifactBasePath("outputs/model-artifacts")).thenReturn(artifactBase.toString());
        AmlModelRegistryService service = new AmlModelRegistryService(
                runRepository, registryRepository, new FileChecksumService(), configService, new ObjectMapper()
        );

        assertThrows(IllegalArgumentException.class,
                () -> service.registerCandidate(runId, request("0".repeat(64))));

        verify(registryRepository, never()).insertCandidate(any());
        verify(runRepository, never()).completeCandidate(eq(runId), any(), eq(240L));
    }

    @Test
    void startsTrainingOnlyFromDatasetReadyState() {
        UUID runId = UUID.randomUUID();
        AmlTrainingRunRepository runRepository = mock(AmlTrainingRunRepository.class);
        AmlModelRegistryRepository registryRepository = mock(AmlModelRegistryRepository.class);
        AmlTrainingRun ready = run(runId, "DATASET_READY", 250L);
        AmlTrainingRun training = run(runId, "TRAINING", 250L);
        when(runRepository.findRequired(runId)).thenReturn(ready, training);
        when(runRepository.startTraining(runId, null)).thenReturn(true);
        AmlModelRegistryService service = new AmlModelRegistryService(
                runRepository, registryRepository, new FileChecksumService(), mock(AppConfigService.class),
                new ObjectMapper()
        );

        assertEquals("TRAINING", service.startTraining(runId, null).status());
    }

    private RegisterCandidateModelRequest request(String checksum) {
        return new RegisterCandidateModelRequest(
                "HST-RETAIL-20260804-01", "HST-RETAIL-20260804-01", checksum,
                "1".repeat(64), 240L, 0.02, 240L, 5L,
                0.35, 0.80, 0.95, Map.of("trees", 25), Map.of("durationMs", 1200), "trainer"
        );
    }

    private void writeManifest(Path artifactBundle, UUID runId) throws Exception {
        Files.writeString(artifactBundle.resolve("artifact-manifest.json"), """
                {
                  "modelVersion": "HST-RETAIL-20260804-01",
                  "modelType": "HALF_SPACE_TREES",
                  "modelSegment": "RETAIL_GENERAL",
                  "featureVersion": "AML_FEATURES_V2",
                  "trainingRunId": "%s",
                  "datasetChecksum": "%s",
                  "baseModelVersion": null,
                  "featureSchemaChecksum": "%s",
                  "learnedRowCount": 240
                }
                """.formatted(runId, "d".repeat(64), "1".repeat(64)));
    }

    private AmlTrainingRun run(UUID runId, String status, long exportedRows) {
        return new AmlTrainingRun(
                runId, AmlTrainingType.DAILY_INCREMENTAL, "AML_FEATURES_V2",
                "HALF_SPACE_TREES", "RETAIL_GENERAL",
                LocalDate.of(2026, 8, 4), LocalDate.of(2026, 8, 4),
                LocalDateTime.of(2026, 8, 4, 23, 59, 59),
                exportedRows, exportedRows, null, "outputs/dataset", "d".repeat(64),
                null, null, status, null, Instant.parse("2026-08-05T00:00:00Z"),
                null, Instant.parse("2026-08-05T00:00:00Z")
        );
    }
}
