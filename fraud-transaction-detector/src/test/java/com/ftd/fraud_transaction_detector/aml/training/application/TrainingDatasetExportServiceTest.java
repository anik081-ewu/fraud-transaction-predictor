package com.ftd.fraud_transaction_detector.aml.training.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingRun;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingType;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.AmlTrainingRunRepository;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.EligibleFeatureReader;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.FileChecksumService;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.ParquetDatasetWriter;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static com.ftd.fraud_transaction_detector.aml.training.infrastructure.ParquetDatasetWriterTest.row;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrainingDatasetExportServiceTest {

    @Test
    void publishesManifestAndChecksummedParquetParts() throws Exception {
        UUID id = UUID.randomUUID();
        Path temporaryDirectory = Path.of("target", "test-output", "dataset-export-" + id).toAbsolutePath();
        AmlTrainingRun run = run(id);
        AmlTrainingRunRepository runRepository = mock(AmlTrainingRunRepository.class);
        EligibleFeatureReader reader = mock(EligibleFeatureReader.class);
        BusinessDayService businessDayService = mock(BusinessDayService.class);
        HistoricalFeatureMaterializationService materializationService = mock(HistoricalFeatureMaterializationService.class);
        AppConfigService configService = mock(AppConfigService.class);
        when(runRepository.findRequired(id)).thenReturn(run);
        when(reader.count(run)).thenReturn(2L);
        LocalDateTime initialCursor = LocalDateTime.of(1, 1, 1, 0, 0);
        LocalDateTime rowDate = LocalDateTime.of(2026, 8, 4, 12, 0);
        when(reader.readAfter(eq(run), eq(initialCursor), eq(0L), eq(2))).thenReturn(List.of(row(1), row(2)));
        when(reader.readAfter(eq(run), eq(rowDate), eq(2L), eq(2))).thenReturn(List.of());
        when(configService.getExportBasePath("outputs/training-datasets"))
                .thenReturn(temporaryDirectory.toString());
        when(configService.getExportChunkSize(50_000)).thenReturn(2);
        when(configService.getExportRowsPerFile(100_000)).thenReturn(2);
        var service = new TrainingDatasetExportService(
                runRepository, reader, businessDayService, new ParquetDatasetWriter(),
                new FileChecksumService(), configService,
                new ObjectMapper().findAndRegisterModules(),
                materializationService,
                Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC)
        );

        var manifest = service.export(id);

        assertEquals(2, manifest.rowCount());
        assertEquals(1, manifest.files().size());
        Path published = temporaryDirectory
                .resolve("fv=AML_FEATURES_V2")
                .resolve("segment=RETAIL_GENERAL")
                .resolve("range=2026-08-04_2026-08-04")
                .resolve("run=" + id);
        assertTrue(Files.exists(published.resolve("manifest.json")));
        assertTrue(Files.exists(published.resolve("part-00001.parquet")));
        verify(runRepository).complete(eq(id), eq(2L), eq(published.toString()), eq(manifest.datasetChecksum()));
        verify(materializationService).normalize(run);
        verify(materializationService).materialize(run);
        verify(reader).readAfter(eq(run), eq(initialCursor), eq(0L), eq(2));
        verify(reader).readAfter(eq(run), eq(rowDate), eq(2L), eq(2));
    }

    private AmlTrainingRun run(UUID id) {
        return new AmlTrainingRun(
                id, AmlTrainingType.DAILY_INCREMENTAL, "AML_FEATURES_V2",
                "HALF_SPACE_TREES", "RETAIL_GENERAL",
                LocalDate.of(2026, 8, 4), LocalDate.of(2026, 8, 4),
                LocalDateTime.of(2026, 8, 4, 23, 59, 59),
                null, null, null, null, null, null, null, "QUEUED", null,
                null, null, Instant.parse("2026-08-05T00:00:00Z")
        );
    }
}
