package com.ftd.fraud_transaction_detector.aml.training.application;

import com.ftd.fraud_transaction_detector.aml.training.api.RegisterCandidateModelRequest;
import com.ftd.fraud_transaction_detector.aml.training.client.IncrementalTrainingClient;
import com.ftd.fraud_transaction_detector.aml.training.client.IncrementalTrainingRequest;
import com.ftd.fraud_transaction_detector.aml.training.client.IncrementalTrainingResponse;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlModelRegistryEntry;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingRun;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.AmlModelRegistryRepository;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.AmlTrainingRunRepository;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

@Service
public class IncrementalTrainingService {

    private static final Set<String> SUPPORTED_MODEL_TYPES = Set.of(
            "HALF_SPACE_TREES", "ONLINE_ONE_CLASS_SVM"
    );

    private final AmlTrainingRunRepository runRepository;
    private final AmlModelRegistryRepository registryRepository;
    private final AmlModelRegistryService registryService;
    private final IncrementalTrainingClient trainingClient;
    private final AppConfigService appConfigService;
    private final Clock clock;

    @Autowired
    public IncrementalTrainingService(
            AmlTrainingRunRepository runRepository,
            AmlModelRegistryRepository registryRepository,
            AmlModelRegistryService registryService,
            IncrementalTrainingClient trainingClient,
            AppConfigService appConfigService
    ) {
        this(runRepository, registryRepository, registryService, trainingClient, appConfigService, Clock.systemUTC());
    }

    IncrementalTrainingService(
            AmlTrainingRunRepository runRepository,
            AmlModelRegistryRepository registryRepository,
            AmlModelRegistryService registryService,
            IncrementalTrainingClient trainingClient,
            AppConfigService appConfigService,
            Clock clock
    ) {
        this.runRepository = runRepository;
        this.registryRepository = registryRepository;
        this.registryService = registryService;
        this.trainingClient = trainingClient;
        this.appConfigService = appConfigService;
        this.clock = clock;
    }

    public AmlTrainingRun start(UUID trainingRunId, String baseModelVersion) {
        AmlTrainingRun run = runRepository.findRequired(trainingRunId);
        if (!SUPPORTED_MODEL_TYPES.contains(run.modelType())) {
            throw new IllegalArgumentException(
                    "Incremental training supports HALF_SPACE_TREES and ONLINE_ONE_CLASS_SVM"
            );
        }
        return registryService.startTraining(trainingRunId, baseModelVersion);
    }

    public AmlModelRegistryEntry train(UUID trainingRunId, String requestedBy) {
        AmlTrainingRun run = runRepository.findRequired(trainingRunId);
        try {
            if (!"TRAINING".equals(run.status())) {
                throw new IllegalStateException("Training run must be TRAINING before Python execution");
            }
            String modelVersion = modelVersion(run);
            String baseModelPath = run.baseModelVersion() == null ? null
                    : registryRepository.findRequired(run.baseModelVersion()).artifactPath();
            Path artifactBase = Path.of(appConfigService.getModelArtifactBasePath("outputs/model-artifacts"))
                    .toAbsolutePath().normalize();
            IncrementalTrainingResponse response = trainingClient.train(new IncrementalTrainingRequest(
                    run.trainingRunId().toString(), run.datasetPath(), run.datasetChecksum(),
                    artifactBase.toString(), modelVersion, run.modelType(), run.modelSegment(),
                    run.featureVersion(), baseModelPath, parameters(run.modelType())
            ));
            if (!"CANDIDATE_READY".equals(response.status()) || !modelVersion.equals(response.modelVersion())) {
                throw new IllegalStateException("Python returned an invalid candidate-training result");
            }
            return registryService.registerCandidate(trainingRunId, new RegisterCandidateModelRequest(
                    response.modelVersion(), response.artifactPath(), response.artifactChecksum(),
                    response.featureSchemaChecksum(), response.learnedRowCount(), response.anomalyRate(),
                    response.validationRowCount(), response.alertCount(), response.averageScore(),
                    response.scoreP95(), response.scoreP99(), response.parameters(), response.metrics(),
                    normalizeActor(requestedBy)
            ));
        } catch (Exception exception) {
            try {
                registryService.failTraining(trainingRunId, exception.getMessage());
            } catch (Exception ignored) {
            }
            throw new IllegalStateException("Incremental model training failed for run " + trainingRunId, exception);
        }
    }

    private String modelVersion(AmlTrainingRun run) {
        String segment = run.modelSegment() == null ? "GLOBAL" : run.modelSegment();
        segment = segment.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "-");
        String date = LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE);
        String prefix = "HALF_SPACE_TREES".equals(run.modelType()) ? "HST" : "OCSVM";
        return prefix + "-" + segment + "-" + date + "-" + run.trainingRunId().toString().substring(0, 8);
    }

    private Map<String, Object> parameters(String modelType) {
        return "ONLINE_ONE_CLASS_SVM".equals(modelType)
                ? appConfigService.getOnlineOneClassSvmParameters()
                : appConfigService.getHstParameters();
    }

    private String normalizeActor(String requestedBy) {
        return requestedBy == null || requestedBy.isBlank() ? "python-ml-service" : requestedBy.trim();
    }
}
