package com.ftd.fraud_transaction_detector.aml.training.infrastructure;

import com.ftd.fraud_transaction_detector.aml.training.domain.ExportFeatureRow;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParquetDatasetWriterTest {

    @Test
    void writesParquetMagicAndNonEmptyTypedDataset() throws Exception {
        Path temporaryDirectory = Path.of("target", "test-output", "parquet-" + UUID.randomUUID());
        Path output = temporaryDirectory.resolve("part-00001.parquet");

        new ParquetDatasetWriter().write(output, List.of(row(1)));

        byte[] bytes = Files.readAllBytes(output);
        assertTrue(bytes.length > 8);
        assertArrayEquals("PAR1".getBytes(), java.util.Arrays.copyOfRange(bytes, 0, 4));
        assertArrayEquals("PAR1".getBytes(), java.util.Arrays.copyOfRange(bytes, bytes.length - 4, bytes.length));
    }

    public static ExportFeatureRow row(long id) {
        return new ExportFeatureRow(
                id, "T-" + id, "C-1", "A-1", LocalDate.of(2026, 8, 4),
                LocalDateTime.of(2026, 8, 4, 12, 0), "AML_FEATURES_V2",
                "LEGACY_MODEL_INPUT_V1", "{\"transaction_hour\":12}",
                100, 1000.0, 0.1, 12, 1, false, false,
                30, 30, 30, 1.0, 90.0, 85.0, 10.0,
                1.1, 1.2, 1.0, 1, 3, 10, 20,
                300, 700, 1200, false, false, false, false, false,
                "RETAIL_GENERAL", 95.0, 12.0, 1.05, 0.4, null
        );
    }
}
