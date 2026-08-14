package com.ftd.fraud_transaction_detector.aml.research.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.aml.research.api.RunGrowthAnalysisRequest;
import com.ftd.fraud_transaction_detector.aml.research.client.GrowthAnalysisResponse;
import com.ftd.fraud_transaction_detector.aml.research.domain.SupervisedGrowthStudy;
import com.ftd.fraud_transaction_detector.aml.research.infrastructure.SupervisedGrowthStudyRepository;
import com.ftd.fraud_transaction_detector.aml.training.application.TrainingDatasetExportService;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingRun;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class SupervisedGrowthStudyService {

    private static final Logger log = LoggerFactory.getLogger(SupervisedGrowthStudyService.class);

    private final SupervisedGrowthStudyRepository repository;
    private final GrowthAnalysisService analysisService;
    private final TrainingDatasetExportService datasetService;
    private final AppConfigService appConfigService;
    private final ObjectMapper objectMapper;

    public SupervisedGrowthStudyService(
            SupervisedGrowthStudyRepository repository,
            GrowthAnalysisService analysisService,
            TrainingDatasetExportService datasetService,
            AppConfigService appConfigService,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.analysisService = analysisService;
        this.datasetService = datasetService;
        this.appConfigService = appConfigService;
        this.objectMapper = objectMapper;
    }

    public QueueResult queueOrReuse(UUID trainingRunId, String requestedBy) {
        requireSupervisedSnapshot(trainingRunId);
        Optional<SupervisedGrowthStudy> reusable = repository.findReusable(trainingRunId);
        if (reusable.isPresent()) return new QueueResult(reusable.get(), false);
        UUID studyId = repository.create(trainingRunId, normalizeActor(requestedBy));
        return new QueueResult(require(studyId), true);
    }

    public void runAndPersist(UUID studyId, UUID trainingRunId, RunGrowthAnalysisRequest options) {
        repository.markRunning(studyId);
        try {
            GrowthAnalysisResponse response = analysisService.analyze(trainingRunId, options);
            if (!"SUPERVISED".equalsIgnoreCase(String.valueOf(response.methodology().get("learningMode")))) {
                throw new IllegalStateException("Supervised study received a non-supervised analysis response");
            }
            repository.complete(studyId, objectMapper.writeValueAsString(response));
            log.info("Supervised growth study {} completed with {} cells", studyId, response.results().size());
        } catch (Exception exception) {
            log.error("Supervised growth study {} failed: {}", studyId, exception.getMessage(), exception);
            repository.markFailed(studyId, exception.getMessage());
        }
    }

    public Optional<SupervisedGrowthStudy> latestRelevant() {
        return repository.latestRelevant();
    }

    public SupervisedGrowthStudy require(UUID studyId) {
        return repository.find(studyId)
                .orElseThrow(() -> new IllegalArgumentException("Supervised growth study not found: " + studyId));
    }

    private void requireSupervisedSnapshot(UUID trainingRunId) {
        if (!appConfigService.isSupervisedLearningMode()) {
            throw new IllegalStateException("Set System Type to Supervised Learning before running this comparison");
        }
        AmlTrainingRun run = datasetService.getRun(trainingRunId);
        if (run.datasetPath() == null || run.datasetChecksum() == null) {
            throw new IllegalStateException("Supervised comparison requires a verified training snapshot");
        }
        if (!run.modelType().startsWith("SUPERVISED_")
                && !java.util.Set.of("XGBOOST_CLASSIFIER", "RANDOM_FOREST_CLASSIFIER", "LOGISTIC_REGRESSION")
                .contains(run.modelType())) {
            throw new IllegalArgumentException("Selected snapshot was not created for supervised learning");
        }
    }

    private String normalizeActor(String requestedBy) {
        return requestedBy == null || requestedBy.isBlank() ? "supervised-comparison-ui" : requestedBy.trim();
    }

    public record QueueResult(SupervisedGrowthStudy study, boolean created) {
    }
}
