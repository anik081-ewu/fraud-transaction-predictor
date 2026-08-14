package com.ftd.fraud_transaction_detector.aml.research.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.aml.research.client.RealModelAgreementClient;
import com.ftd.fraud_transaction_detector.aml.research.domain.AgreementStudy;
import com.ftd.fraud_transaction_detector.aml.research.infrastructure.AgreementStudyRepository;
import com.ftd.fraud_transaction_detector.aml.training.application.TrainingDatasetExportService;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlModelRegistryEntry;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingRun;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.AmlModelRegistryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Measures whether the trained models flag the same transactions or different ones.
 *
 * Like the growth study this is far too slow for a page load, so it is computed once against
 * a snapshot and stored; the UI then reads the saved matrix instantly.
 */
@Service
public class AgreementStudyService {

    private static final Logger log = LoggerFactory.getLogger(AgreementStudyService.class);

    private final AgreementStudyRepository repository;
    private final RealModelAgreementClient client;
    private final TrainingDatasetExportService datasetService;
    private final AmlModelRegistryRepository registryRepository;
    private final ObjectMapper objectMapper;

    public AgreementStudyService(
            AgreementStudyRepository repository,
            RealModelAgreementClient client,
            TrainingDatasetExportService datasetService,
            AmlModelRegistryRepository registryRepository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.client = client;
        this.datasetService = datasetService;
        this.registryRepository = registryRepository;
        this.objectMapper = objectMapper;
    }

    public UUID queue(UUID trainingRunId, String requestedBy) {
        AmlTrainingRun run = datasetService.getRun(trainingRunId);
        if (run.datasetPath() == null || run.datasetChecksum() == null) {
            throw new IllegalStateException(
                    "Model agreement needs a run with a verified exported dataset: " + trainingRunId);
        }
        return repository.create(trainingRunId, normalizeActor(requestedBy));
    }

    public void runAndPersist(UUID studyId, UUID trainingRunId) {
        repository.markRunning(studyId);
        try {
            AmlTrainingRun run = datasetService.getRun(trainingRunId);
            Map<String, Object> request = new HashMap<>();
            request.put("datasetPath", run.datasetPath());
            request.put("datasetChecksum", run.datasetChecksum());
            request.put("modelBundlePath", modelArtifactDirectory(run));

            Map<String, Object> result = client.analyze(request);
            repository.complete(
                    studyId,
                    longValue(result.get("evaluatedRows")),
                    intValue(result.get("modelCount")),
                    objectMapper.writeValueAsString(result)
            );
            log.info("Agreement study {} completed over {} models", studyId, result.get("modelCount"));
        } catch (Exception exception) {
            log.error("Agreement study {} failed: {}", studyId, exception.getMessage(), exception);
            repository.markFailed(studyId, exception.getMessage());
        }
    }

    public Optional<AgreementStudy> latestRelevant() {
        return repository.findLatestRelevant();
    }

    public AgreementStudy require(UUID studyId) {
        return repository.find(studyId)
                .orElseThrow(() -> new IllegalArgumentException("Agreement study not found: " + studyId));
    }

    public List<AgreementStudy> listRecent() {
        return repository.listRecent(20);
    }

    private String modelArtifactDirectory(AmlTrainingRun run) {
        for (String modelType : List.of("ISOLATION_FOREST", "AUTOENCODER", "LOCAL_OUTLIER_FACTOR")) {
            String path = artifactPath(modelType, run);
            if (path != null) return path;
        }
        return null;
    }

    private String artifactPath(String modelType, AmlTrainingRun run) {
        return registryRepository
                .findLatestCompatible(modelType, run.featureVersion(), run.modelSegment())
                .map(AmlModelRegistryEntry::artifactPath)
                .orElse(null);
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Integer intValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private String normalizeActor(String requestedBy) {
        return requestedBy == null || requestedBy.isBlank() ? "model-comparison-ui" : requestedBy.trim();
    }
}
