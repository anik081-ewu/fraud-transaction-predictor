package com.ftd.fraud_transaction_detector.aml.research.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.aml.research.api.RunGrowthAnalysisRequest;
import com.ftd.fraud_transaction_detector.aml.research.client.GrowthAnalysisResponse;
import com.ftd.fraud_transaction_detector.aml.research.domain.GrowthMetric;
import com.ftd.fraud_transaction_detector.aml.research.domain.GrowthStudy;
import com.ftd.fraud_transaction_detector.aml.research.infrastructure.GrowthStudyRepository;
import com.ftd.fraud_transaction_detector.aml.training.application.TrainingDatasetExportService;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns the growth analysis from a multi-minute blocking call into a stored artifact.
 *
 * The analysis trains every detector at every partition, so it can never be run on page
 * load. Studies are executed once and persisted; the UI then reads rows instantly, the same
 * way the model-comparison table reads the model registry.
 */
@Service
public class GrowthStudyService {

    private static final Logger log = LoggerFactory.getLogger(GrowthStudyService.class);

    private final GrowthStudyRepository repository;
    private final GrowthAnalysisService analysisService;
    private final TrainingDatasetExportService datasetService;
    private final ObjectMapper objectMapper;

    public GrowthStudyService(
            GrowthStudyRepository repository,
            GrowthAnalysisService analysisService,
            TrainingDatasetExportService datasetService,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.analysisService = analysisService;
        this.datasetService = datasetService;
        this.objectMapper = objectMapper;
    }

    /** Validates up front so the caller gets a synchronous error, then queues the study. */
    public UUID queue(UUID trainingRunId, String requestedBy) {
        AmlTrainingRun run = datasetService.getRun(trainingRunId);
        if (run.datasetPath() == null || run.datasetChecksum() == null) {
            throw new IllegalStateException(
                    "Growth analysis needs a run with a verified exported dataset: " + trainingRunId);
        }
        return repository.create(trainingRunId, normalizeActor(requestedBy));
    }

    /**
     * Runs the analysis and stores the result. Intended to be called from an async launcher;
     * failures are recorded on the study rather than thrown, so the caller has nothing to
     * handle and the UI can show why a study did not finish.
     */
    public void runAndPersist(UUID studyId, UUID trainingRunId, RunGrowthAnalysisRequest options) {
        repository.markRunning(studyId);
        try {
            GrowthAnalysisResponse response = analysisService.analyze(trainingRunId, options);
            List<GrowthMetric> metrics = toMetrics(response);
            if (metrics.isEmpty()) {
                throw new IllegalStateException("Growth analysis returned no detector results");
            }
            repository.complete(
                    studyId,
                    response.featureVersion(),
                    response.datasetRows(),
                    response.featureCount(),
                    response.partitionPercentages(),
                    writeJson(response.methodology()),
                    metrics
            );
            log.info("Growth study {} completed with {} cells", studyId, metrics.size());
        } catch (Exception exception) {
            log.error("Growth study {} failed: {}", studyId, exception.getMessage(), exception);
            repository.markFailed(studyId, exception.getMessage());
        }
    }

    public List<GrowthStudy> listRecent() {
        return repository.listRecent(20);
    }

    /** In-flight study if one exists, else the newest completed result, else the newest failure. */
    public Optional<GrowthStudy> latestRelevant() {
        return repository.findLatestRelevant();
    }

    public GrowthStudy require(UUID studyId) {
        return repository.find(studyId)
                .orElseThrow(() -> new IllegalArgumentException("Growth study not found: " + studyId));
    }

    private List<GrowthMetric> toMetrics(GrowthAnalysisResponse response) {
        if (response.results() == null) return List.of();
        return response.results().stream()
                .filter(row -> row.get("detector") != null)
                .map(this::toMetric)
                .toList();
    }

    private GrowthMetric toMetric(Map<String, Object> row) {
        return new GrowthMetric(
                string(row, "detector"),
                intValue(row, "partitionPercentage"),
                longValue(row, "partitionRows"),
                longValue(row, "trainingRows"),
                longValue(row, "learnedRows"),
                longValue(row, "evaluationRows"),
                doubleValue(row, "excessMassAuc"),
                doubleValue(row, "scoreSkewness"),
                doubleValue(row, "rankStability"),
                doubleValue(row, "anomalyRate"),
                longValue(row, "alertCount"),
                doubleValue(row, "threshold"),
                doubleValue(row, "averageScore"),
                doubleValue(row, "scoreP50"),
                doubleValue(row, "scoreP95"),
                doubleValue(row, "scoreP99"),
                doubleValue(row, "trainingDurationMs"),
                doubleValue(row, "rowsPerSecond"),
                Boolean.TRUE.equals(row.get("boundedTrainingSample"))
        );
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception exception) {
            return null;
        }
    }

    private String string(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private int intValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private Long longValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.longValue() : null;
    }

    private Double doubleValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (!(value instanceof Number number)) return null;
        double result = number.doubleValue();
        return Double.isFinite(result) ? result : null;
    }

    private String normalizeActor(String requestedBy) {
        return requestedBy == null || requestedBy.isBlank() ? "growth-study-ui" : requestedBy.trim();
    }
}
