package com.ftd.fraud_transaction_detector.aml.training.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.aml.deployment.application.ModelDeploymentService;
import com.ftd.fraud_transaction_detector.aml.training.api.RegisterBatchCandidateRequest;
import com.ftd.fraud_transaction_detector.aml.training.api.RegisterCandidateModelRequest;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlModelRegistryEntry;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingRun;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.AmlModelRegistryRepository;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.AmlTrainingRunRepository;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.FileChecksumService;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class AmlModelRegistryService {

    private static final String SHA_256_PATTERN = "[a-fA-F0-9]{64}";
    private static final String VERSION_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]{2,99}";

    private final AmlTrainingRunRepository runRepository;
    private final AmlModelRegistryRepository registryRepository;
    private final FileChecksumService checksumService;
    private final AppConfigService appConfigService;
    private final ObjectMapper objectMapper;
    private final ModelDeploymentService deploymentService;

    public AmlModelRegistryService(
            AmlTrainingRunRepository runRepository,
            AmlModelRegistryRepository registryRepository,
            FileChecksumService checksumService,
            AppConfigService appConfigService,
            ObjectMapper objectMapper,
            ModelDeploymentService deploymentService
    ) {
        this.runRepository = runRepository;
        this.registryRepository = registryRepository;
        this.checksumService = checksumService;
        this.appConfigService = appConfigService;
        this.objectMapper = objectMapper;
        this.deploymentService = deploymentService;
    }

    @Transactional
    public AmlTrainingRun startTraining(UUID trainingRunId, String baseModelVersion) {
        AmlTrainingRun run = runRepository.findRequired(trainingRunId);
        String normalizedBaseVersion = normalize(baseModelVersion);
        if (normalizedBaseVersion != null) validateBaseModel(run, normalizedBaseVersion);
        if (!runRepository.startTraining(trainingRunId, normalizedBaseVersion)) {
            throw new IllegalStateException("Training run must be DATASET_READY before training starts: " + trainingRunId);
        }
        return runRepository.findRequired(trainingRunId);
    }

    @Transactional
    public AmlModelRegistryEntry registerCandidate(UUID trainingRunId, RegisterCandidateModelRequest request) {
        AmlTrainingRun run = runRepository.findRequired(trainingRunId);
        if (!"TRAINING".equals(run.status())) {
            throw new IllegalStateException("Training run must be TRAINING before candidate registration");
        }
        validateRequest(run, request);
        Path artifactPath = resolveArtifactPath(request.artifactPath());
        try {
            validateArtifactManifest(run, request, artifactPath);
            String actualChecksum = checksumService.sha256Bundle(artifactPath);
            if (!actualChecksum.equalsIgnoreCase(request.artifactChecksum().trim())) {
                throw new IllegalArgumentException("Candidate artifact checksum does not match the registered checksum");
            }
            AmlModelRegistryEntry candidate = new AmlModelRegistryEntry(
                    request.modelVersion().trim(), run.modelType(), run.modelSegment(), run.featureVersion(),
                    trainingRunId, artifactPath.toString(), actualChecksum, run.datasetChecksum(),
                    run.baseModelVersion(), request.featureSchemaChecksum().trim().toLowerCase(), "CANDIDATE",
                    checksumService.bundleSize(artifactPath), request.learnedRowCount(),
                    request.anomalyRate(), request.validationRowCount(), request.alertCount(),
                    request.averageScore(), request.scoreP95(), request.scoreP99(),
                    json(request.parameters()), json(request.metrics()), request.registeredBy().trim(), null
            );
            registryRepository.insertCandidate(candidate);
            if (!runRepository.completeCandidate(trainingRunId, candidate.modelVersion(), request.learnedRowCount())) {
                throw new IllegalStateException("Training run changed while the candidate was being registered");
            }
            AmlModelRegistryEntry registered = registryRepository.findRequired(candidate.modelVersion());
            deploymentService.autoActivate(registered);
            return registryRepository.findRequired(candidate.modelVersion());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to verify candidate artifact bundle", exception);
        }
    }

    /**
     * Registers a batch model trained by the shared /train call.
     *
     * Differs from {@link #registerCandidate} in that the artifact directory is shared by all
     * batch models, so it is neither named after the model version nor carries a per-model
     * artifact-manifest.json. The bundle checksum is still recorded, so drift in the served
     * artifacts remains detectable — but this path cannot attribute the bundle to one model
     * the way the incremental contract does.
     */
    @Transactional
    public AmlModelRegistryEntry registerBatchCandidate(UUID trainingRunId, RegisterBatchCandidateRequest request) {
        AmlTrainingRun run = runRepository.findRequired(trainingRunId);
        if (!"TRAINING".equals(run.status())) {
            throw new IllegalStateException("Training run must be TRAINING before candidate registration");
        }
        validateBatchRequest(request);
        Path artifactPath = resolveBatchArtifactPath(request.artifactPath());
        try {
            String actualChecksum = checksumService.sha256Bundle(artifactPath);
            AmlModelRegistryEntry candidate = new AmlModelRegistryEntry(
                    request.modelVersion().trim(), run.modelType(), run.modelSegment(), run.featureVersion(),
                    trainingRunId, artifactPath.toString(), actualChecksum, run.datasetChecksum(),
                    run.baseModelVersion(), featureSchemaChecksum(request), "CANDIDATE",
                    checksumService.bundleSize(artifactPath), request.learnedRowCount(),
                    request.anomalyRate(), request.validationRowCount(), request.alertCount(),
                    request.averageScore(), request.scoreP95(), request.scoreP99(),
                    json(request.parameters()), json(request.metrics()), request.registeredBy().trim(), null
            );
            registryRepository.insertCandidate(candidate);
            if (!runRepository.completeCandidate(trainingRunId, candidate.modelVersion(), request.learnedRowCount())) {
                throw new IllegalStateException("Training run changed while the candidate was being registered");
            }
            AmlModelRegistryEntry registered = registryRepository.findRequired(candidate.modelVersion());
            deploymentService.autoActivate(registered);
            return registryRepository.findRequired(candidate.modelVersion());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to verify batch candidate artifact bundle", exception);
        }
    }

    /**
     * Python reports the trained feature schema hash in each model's metrics. Falling back to
     * the bundle checksum keeps the column populated rather than failing registration, since
     * for batch models it is informational only.
     */
    private String featureSchemaChecksum(RegisterBatchCandidateRequest request) {
        Object hash = request.metrics() == null ? null : request.metrics().get("featureSchemaHash");
        String candidate = hash == null ? null : hash.toString().trim().toLowerCase(java.util.Locale.ROOT);
        return candidate != null && candidate.matches(SHA_256_PATTERN) ? candidate : null;
    }

    /**
     * Batch artifacts live in the ML service's own models directory, not under
     * aml.model.artifact_base_path, so {@link #resolveArtifactPath} — which requires
     * containment in that root — rejects them. The path here originates from our own
     * training response rather than a client, so it is validated as an existing directory
     * without the containment requirement.
     *
     * Note this only works while the ML service shares a filesystem with this service; a
     * remote ML service would need the artifacts published somewhere reachable instead.
     */
    private Path resolveBatchArtifactPath(String requestedPath) {
        if (requestedPath == null || requestedPath.isBlank()) {
            throw new IllegalArgumentException("Batch candidate artifactPath is required");
        }
        try {
            Path resolved = Path.of(requestedPath.trim()).toAbsolutePath().normalize();
            if (!Files.isDirectory(resolved)) {
                throw new IllegalArgumentException(
                        "Batch candidate artifact directory does not exist: " + resolved);
            }
            return resolved.toRealPath();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to resolve batch candidate artifact path", exception);
        }
    }

    private void validateBatchRequest(RegisterBatchCandidateRequest request) {
        if (request == null) throw new IllegalArgumentException("Batch candidate request is required");
        if (!request.modelVersion().trim().matches(VERSION_PATTERN)) {
            throw new IllegalArgumentException("modelVersion contains unsupported characters");
        }
        if (request.learnedRowCount() <= 0) {
            throw new IllegalArgumentException("learnedRowCount must be positive");
        }
        requireRate(request.anomalyRate(), "anomalyRate");
        requireFinite(request.averageScore(), "averageScore");
        requireFinite(request.scoreP95(), "scoreP95");
        requireFinite(request.scoreP99(), "scoreP99");
    }

    @Transactional
    public AmlTrainingRun failTraining(UUID trainingRunId, String reason) {
        if (!runRepository.failTraining(trainingRunId, reason)) {
            throw new IllegalStateException("Only a TRAINING run can be marked as training failed");
        }
        return runRepository.findRequired(trainingRunId);
    }

    public AmlModelRegistryEntry get(String modelVersion) {
        return registryRepository.findRequired(modelVersion);
    }

    public List<AmlModelRegistryEntry> search(String status, String modelType, String modelSegment) {
        return registryRepository.search(normalizeUpper(status), normalizeUpper(modelType), normalizeUpper(modelSegment));
    }

    private void validateBaseModel(AmlTrainingRun run, String baseModelVersion) {
        AmlModelRegistryEntry base = registryRepository.findRequired(baseModelVersion);
        if (!List.of("CHAMPION", "CHALLENGER").contains(base.status())) {
            throw new IllegalArgumentException("Base model must be a CHAMPION or CHALLENGER");
        }
        if (!run.modelType().equals(base.modelType())
                || !run.featureVersion().equals(base.featureVersion())
                || !Objects.equals(run.modelSegment(), base.modelSegment())) {
            throw new IllegalArgumentException("Base model is incompatible with the training run contract");
        }
    }

    private void validateRequest(AmlTrainingRun run, RegisterCandidateModelRequest request) {
        if (request == null) throw new IllegalArgumentException("Candidate model request is required");
        if (!request.modelVersion().trim().matches(VERSION_PATTERN)) {
            throw new IllegalArgumentException("modelVersion contains unsupported characters");
        }
        requireSha256(request.artifactChecksum(), "artifactChecksum");
        requireSha256(request.featureSchemaChecksum(), "featureSchemaChecksum");
        if (run.exportedRowCount() == null || request.learnedRowCount() > run.exportedRowCount()) {
            throw new IllegalArgumentException("learnedRowCount cannot exceed the exported dataset row count");
        }
        requireRate(request.anomalyRate(), "anomalyRate");
        requireFinite(request.averageScore(), "averageScore");
        requireFinite(request.scoreP95(), "scoreP95");
        requireFinite(request.scoreP99(), "scoreP99");
    }

    private Path resolveArtifactPath(String requestedPath) {
        try {
            Path configuredBase = Path.of(appConfigService.getModelArtifactBasePath("outputs/model-artifacts"))
                    .toAbsolutePath().normalize();
            Path supplied = Path.of(requestedPath.trim());
            Path requested = supplied.isAbsolute() ? supplied.normalize() : configuredBase.resolve(supplied).normalize();
            if (!Files.isDirectory(requested)) {
                throw new IllegalArgumentException("Candidate artifact bundle directory does not exist");
            }
            Path basePath = configuredBase.toRealPath();
            Path artifactPath = requested.toRealPath();
            if (!artifactPath.startsWith(basePath) || artifactPath.equals(basePath)) {
                throw new IllegalArgumentException("Candidate artifact must be inside the configured artifact directory");
            }
            return artifactPath;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to resolve candidate artifact path", exception);
        }
    }

    private void validateArtifactManifest(
            AmlTrainingRun run,
            RegisterCandidateModelRequest request,
            Path artifactPath
    ) throws IOException {
        if (!artifactPath.getFileName().toString().equals(request.modelVersion().trim())) {
            throw new IllegalArgumentException("Artifact bundle directory must match modelVersion");
        }
        Path manifestPath = artifactPath.resolve("artifact-manifest.json");
        if (!Files.isRegularFile(manifestPath)) {
            throw new IllegalArgumentException("Candidate artifact-manifest.json is required");
        }
        JsonNode manifest = objectMapper.readTree(manifestPath.toFile());
        requireManifestValue(manifest, "modelVersion", request.modelVersion().trim());
        requireManifestValue(manifest, "modelType", run.modelType());
        requireManifestValue(manifest, "featureVersion", run.featureVersion());
        requireManifestValue(manifest, "trainingRunId", run.trainingRunId().toString());
        requireManifestValue(manifest, "datasetChecksum", run.datasetChecksum());
        requireManifestValue(manifest, "featureSchemaChecksum", request.featureSchemaChecksum().trim().toLowerCase());
        requireNullableManifestValue(manifest, "modelSegment", run.modelSegment());
        requireNullableManifestValue(manifest, "baseModelVersion", run.baseModelVersion());
        if (manifest.path("learnedRowCount").asLong(-1) != request.learnedRowCount()) {
            throw new IllegalArgumentException("Artifact manifest learnedRowCount does not match the request");
        }
    }

    private void requireManifestValue(JsonNode manifest, String field, String expected) {
        if (!expected.equals(manifest.path(field).asText(null))) {
            throw new IllegalArgumentException("Artifact manifest " + field + " does not match the training run");
        }
    }

    private void requireNullableManifestValue(JsonNode manifest, String field, String expected) {
        JsonNode value = manifest.get(field);
        String actual = value == null || value.isNull() ? null : value.asText();
        if (!Objects.equals(expected, actual)) {
            throw new IllegalArgumentException("Artifact manifest " + field + " does not match the training run");
        }
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Candidate metadata cannot be serialized", exception);
        }
    }

    private void requireSha256(String value, String field) {
        if (!value.trim().matches(SHA_256_PATTERN)) {
            throw new IllegalArgumentException(field + " must be a SHA-256 checksum");
        }
    }

    private void requireRate(Double value, String field) {
        requireFinite(value, field);
        if (value != null && (value < 0 || value > 1)) {
            throw new IllegalArgumentException(field + " must be between 0 and 1");
        }
    }

    private void requireFinite(Double value, String field) {
        if (value != null && !Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeUpper(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(java.util.Locale.ROOT);
    }
}
