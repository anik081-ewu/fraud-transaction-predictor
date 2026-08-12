package com.ftd.fraud_transaction_detector.aml.training.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.aml.training.api.CreateTrainingRunRequest;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingRun;
import com.ftd.fraud_transaction_detector.aml.training.domain.DatasetManifest;
import com.ftd.fraud_transaction_detector.aml.training.domain.ExportFeatureRow;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.AmlTrainingRunRepository;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.EligibleFeatureReader;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.FileChecksumService;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.ParquetDatasetWriter;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class TrainingDatasetExportService {

    private final AmlTrainingRunRepository runRepository;
    private final EligibleFeatureReader featureReader;
    private final BusinessDayService businessDayService;
    private final ParquetDatasetWriter parquetWriter;
    private final FileChecksumService checksumService;
    private final AppConfigService appConfigService;
    private final HistoricalFeatureMaterializationService featureMaterializationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public TrainingDatasetExportService(
            AmlTrainingRunRepository runRepository,
            EligibleFeatureReader featureReader,
            BusinessDayService businessDayService,
            ParquetDatasetWriter parquetWriter,
            FileChecksumService checksumService,
            AppConfigService appConfigService,
            ObjectMapper objectMapper,
            HistoricalFeatureMaterializationService featureMaterializationService
    ) {
        this(runRepository, featureReader, businessDayService, parquetWriter, checksumService,
                appConfigService, objectMapper, featureMaterializationService, Clock.systemUTC());
    }

    TrainingDatasetExportService(
            AmlTrainingRunRepository runRepository,
            EligibleFeatureReader featureReader,
            BusinessDayService businessDayService,
            ParquetDatasetWriter parquetWriter,
            FileChecksumService checksumService,
            AppConfigService appConfigService,
            ObjectMapper objectMapper,
            HistoricalFeatureMaterializationService featureMaterializationService,
            Clock clock
    ) {
        this.runRepository = runRepository;
        this.featureReader = featureReader;
        this.businessDayService = businessDayService;
        this.parquetWriter = parquetWriter;
        this.checksumService = checksumService;
        this.appConfigService = appConfigService;
        this.objectMapper = objectMapper;
        this.featureMaterializationService = featureMaterializationService;
        this.clock = clock;
    }

    public AmlTrainingRun createRun(CreateTrainingRunRequest request) {
        validate(request);
        return runRepository.create(request);
    }

    public AmlTrainingRun getRun(UUID id) {
        return runRepository.findRequired(id);
    }

    public List<AmlTrainingRun> listRuns() {
        return runRepository.listRecent();
    }

    public AmlTrainingRun queue(UUID id) {
        if (!runRepository.queue(id)) {
            throw new IllegalStateException("Training run is not exportable: " + id);
        }
        return runRepository.findRequired(id);
    }

    public DatasetManifest export(UUID id) {
        AmlTrainingRun run = runRepository.findRequired(id);
        Path temporaryDirectory = null;
        try {
            if (!"QUEUED".equals(run.status())) {
                throw new IllegalStateException("Training run must be QUEUED before export");
            }
            featureMaterializationService.normalize(run);
            businessDayService.requireClosed(run.fromBusinessDate(), run.toBusinessDate());
            featureMaterializationService.materialize(run);
            long requestedRows = featureReader.count(run);
            if (requestedRows == 0) {
                throw new IllegalStateException("No eligible feature rows found for this training run");
            }
            runRepository.markExporting(id, requestedRows);

            Path baseDirectory = Path.of(appConfigService.getExportBasePath("outputs/training-datasets"))
                    .toAbsolutePath().normalize();
            Path finalDirectory = finalDirectory(baseDirectory, run);
            temporaryDirectory = baseDirectory.resolve(".tmp-" + id).normalize();
            requireChild(baseDirectory, finalDirectory);
            requireChild(baseDirectory, temporaryDirectory);
            if (Files.exists(finalDirectory) || Files.exists(temporaryDirectory)) {
                throw new IllegalStateException("Dataset output already exists for training run " + id);
            }
            Files.createDirectories(temporaryDirectory);

            int chunkSize = Math.min(
                    appConfigService.getExportChunkSize(50_000),
                    appConfigService.getExportRowsPerFile(100_000)
            );
            List<DatasetManifest.PartFile> parts = new ArrayList<>();
            Set<String> modelFeatureColumns = new TreeSet<>();
            long lastId = 0;
            LocalDateTime lastTransactionDate = LocalDateTime.of(1, 1, 1, 0, 0);
            long exportedRows = 0;
            int partNumber = 1;
            while (true) {
                List<ExportFeatureRow> rows = featureReader.readAfter(
                        run, lastTransactionDate, lastId, chunkSize
                );
                if (rows.isEmpty()) break;
                String fileName = "part-%05d.parquet".formatted(partNumber++);
                Path partPath = temporaryDirectory.resolve(fileName);
                collectModelFeatureColumns(rows, modelFeatureColumns);
                parquetWriter.write(partPath, rows);
                String checksum = checksumService.sha256(partPath);
                parts.add(new DatasetManifest.PartFile(
                        fileName, rows.size(), Files.size(partPath), checksum
                ));
                exportedRows += rows.size();
                runRepository.updateProgress(id, "EXPORTING", exportedRows, requestedRows);
                ExportFeatureRow lastRow = rows.get(rows.size() - 1);
                lastTransactionDate = lastRow.transactionDate();
                lastId = lastRow.id();
            }
            if (exportedRows != requestedRows) {
                throw new IllegalStateException(
                        "Eligible row set changed during export; requested=" + requestedRows
                                + ", exported=" + exportedRows
                );
            }

            String datasetChecksum = datasetChecksum(parts);
            DatasetManifest manifest = new DatasetManifest(
                    id, run.featureVersion(), run.modelType(), run.modelSegment(),
                    run.fromBusinessDate(), run.toBusinessDate(), run.cutoffTimestamp(),
                    exportedRows, datasetChecksum, parquetWriter.columns(), List.copyOf(modelFeatureColumns),
                    List.copyOf(parts),
                    Instant.now(clock)
            );
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(temporaryDirectory.resolve("manifest.json").toFile(), manifest);
            publish(temporaryDirectory, finalDirectory);
            runRepository.complete(id, exportedRows, finalDirectory.toString(), datasetChecksum);
            return manifest;
        } catch (Exception exception) {
            runRepository.fail(id, exception.getMessage());
            deleteTemporary(temporaryDirectory);
            throw new IllegalStateException("Dataset export failed for training run " + id, exception);
        }
    }

    private Path finalDirectory(Path baseDirectory, AmlTrainingRun run) {
        return baseDirectory
                .resolve("fv=" + safePathPart(run.featureVersion()))
                .resolve("segment=" + safePathPart(run.modelSegment() == null ? "GLOBAL" : run.modelSegment()))
                .resolve("range=" + run.fromBusinessDate() + "_" + run.toBusinessDate())
                .resolve("run=" + run.trainingRunId())
                .normalize();
    }

    private String datasetChecksum(List<DatasetManifest.PartFile> parts) {
        StringBuilder source = new StringBuilder();
        parts.forEach(part -> source.append(part.path()).append(':')
                .append(part.rowCount()).append(':').append(part.sha256()).append('\n'));
        return checksumService.sha256(source.toString());
    }

    private void collectModelFeatureColumns(List<ExportFeatureRow> rows, Set<String> target) throws IOException {
        for (ExportFeatureRow row : rows) {
            Map<String, Object> features = objectMapper.readValue(
                    row.modelFeaturesJson(), new TypeReference<>() {}
            );
            target.addAll(features.keySet());
        }
        if (target.isEmpty()) throw new IllegalStateException("Exported rows contain no model features");
    }

    private void publish(Path temporaryDirectory, Path finalDirectory) throws IOException {
        Files.createDirectories(finalDirectory.getParent());
        try {
            Files.move(temporaryDirectory, finalDirectory, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryDirectory, finalDirectory);
        }
    }

    private void deleteTemporary(Path temporaryDirectory) {
        if (temporaryDirectory == null || !Files.exists(temporaryDirectory)) return;
        try (var paths = Files.walk(temporaryDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private void requireChild(Path baseDirectory, Path candidate) {
        if (!candidate.startsWith(baseDirectory) || candidate.equals(baseDirectory)) {
            throw new IllegalArgumentException("Unsafe export path: " + candidate);
        }
    }

    private String safePathPart(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private void validate(CreateTrainingRunRequest request) {
        if (request == null) throw new IllegalArgumentException("Training run request is required");
        if (request.fromBusinessDate().isAfter(request.toBusinessDate())) {
            throw new IllegalArgumentException("fromBusinessDate must not be after toBusinessDate");
        }
        if (request.cutoffTimestamp().toLocalDate().isBefore(request.toBusinessDate())) {
            throw new IllegalArgumentException("cutoffTimestamp must include the requested business-date range");
        }
    }
}
