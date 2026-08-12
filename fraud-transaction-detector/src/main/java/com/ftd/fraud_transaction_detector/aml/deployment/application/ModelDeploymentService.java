package com.ftd.fraud_transaction_detector.aml.deployment.application;

import com.ftd.fraud_transaction_detector.aml.deployment.api.ModelDeploymentRequest;
import com.ftd.fraud_transaction_detector.aml.deployment.domain.ActiveModelPointer;
import com.ftd.fraud_transaction_detector.aml.deployment.domain.ModelDeploymentEvent;
import com.ftd.fraud_transaction_detector.aml.deployment.infrastructure.ModelDeploymentRepository;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlModelRegistryEntry;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.AmlModelRegistryRepository;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.FileChecksumService;
import com.ftd.fraud_transaction_detector.auth.dto.UserResponse;
import com.ftd.fraud_transaction_detector.auth.service.AuthService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ModelDeploymentService {

    private static final List<String> AUTHORIZED_ROLES = List.of("ADMIN", "AML_ADMIN");

    private final AmlModelRegistryRepository registryRepository;
    private final ModelDeploymentRepository deploymentRepository;
    private final FileChecksumService checksumService;
    private final AuthService authService;
    private final Clock clock;

    @Autowired
    public ModelDeploymentService(
            AmlModelRegistryRepository registryRepository,
            ModelDeploymentRepository deploymentRepository,
            FileChecksumService checksumService,
            AuthService authService
    ) {
        this(registryRepository, deploymentRepository, checksumService, authService, Clock.systemUTC());
    }

    ModelDeploymentService(
            AmlModelRegistryRepository registryRepository,
            ModelDeploymentRepository deploymentRepository,
            FileChecksumService checksumService,
            AuthService authService,
            Clock clock
    ) {
        this.registryRepository = registryRepository;
        this.deploymentRepository = deploymentRepository;
        this.checksumService = checksumService;
        this.authService = authService;
        this.clock = clock;
    }

    @Transactional
    public void autoActivate(AmlModelRegistryEntry candidate) {
        String previous = deploymentRepository
                .findCompatiblePointer(candidate.modelType(), candidate.modelSegment())
                .map(ActiveModelPointer::activeModelVersion)
                .orElse(null);
        deploymentRepository.autoPromote(candidate, previous, candidate.registeredBy());
    }

    @Transactional
    public ModelDeploymentEvent promote(
            String modelVersion,
            ModelDeploymentRequest request,
            String authorizationHeader
    ) {
        UserResponse actor = authorizedUser(authorizationHeader);
        validateRequest(request);
        ModelDeploymentEvent replay = deploymentRepository.findEvent(request.actionId()).orElse(null);
        if (replay != null) return requireReplay(replay, "PROMOTION", modelVersion);

        AmlModelRegistryEntry candidate = registryRepository.findRequired(modelVersion);
        if (!"VALIDATED".equals(candidate.status())) {
            throw new IllegalStateException("Only a VALIDATED model can be promoted");
        }
        verifyArtifact(candidate);
        ActiveModelPointer pointer = deploymentRepository
                .lockPointer(candidate.modelType(), candidate.modelSegment()).orElse(null);
        String previous = pointer == null ? null : pointer.activeModelVersion();
        if (modelVersion.equals(previous)) throw new IllegalStateException("Model is already the active champion");
        if (previous != null) verifyCompatible(candidate, registryRepository.findRequired(previous));

        ModelDeploymentEvent event = event(
                request, "PROMOTION", candidate, previous, candidate.modelVersion(), actor.username()
        );
        deploymentRepository.promote(candidate, pointer, event);
        return event;
    }

    @Transactional
    public ModelDeploymentEvent rollback(
            String modelVersion,
            ModelDeploymentRequest request,
            String authorizationHeader
    ) {
        UserResponse actor = authorizedUser(authorizationHeader);
        validateRequest(request);
        ModelDeploymentEvent replay = deploymentRepository.findEvent(request.actionId()).orElse(null);
        if (replay != null) {
            if (!"ROLLBACK".equals(replay.deploymentAction())
                    || !modelVersion.equals(replay.previousModelVersion())) {
                throw new IllegalArgumentException("actionId was already used for a different deployment action");
            }
            return replay;
        }

        AmlModelRegistryEntry current = registryRepository.findRequired(modelVersion);
        ActiveModelPointer pointer = deploymentRepository
                .lockPointer(current.modelType(), current.modelSegment())
                .orElseThrow(() -> new IllegalStateException("No active-model pointer exists for this model scope"));
        if (!modelVersion.equals(pointer.activeModelVersion()) || !"CHAMPION".equals(current.status())) {
            throw new IllegalStateException("Rollback must target the active CHAMPION model");
        }
        if (pointer.previousModelVersion() == null) {
            throw new IllegalStateException("No previous champion is available for rollback");
        }
        AmlModelRegistryEntry previous = registryRepository.findRequired(pointer.previousModelVersion());
        verifyCompatible(current, previous);
        verifyArtifact(previous);
        ModelDeploymentEvent event = event(
                request, "ROLLBACK", current, current.modelVersion(), previous.modelVersion(), actor.username()
        );
        deploymentRepository.rollback(current, previous, event);
        return event;
    }

    public List<ModelDeploymentEvent> history(String modelType, String modelSegment) {
        if (modelType == null || modelType.isBlank()) {
            throw new IllegalArgumentException("modelType is required");
        }
        return deploymentRepository.history(modelType.trim().toUpperCase(), normalize(modelSegment));
    }

    public List<ActiveModelPointer> activeModels() {
        return deploymentRepository.listPointers();
    }

    private UserResponse authorizedUser(String authorizationHeader) {
        UserResponse user = authService.getCurrentUser(authorizationHeader);
        if (!user.active() || user.roleName() == null
                || !AUTHORIZED_ROLES.contains(user.roleName().trim().toUpperCase())) {
            throw new IllegalArgumentException("Model promotion and rollback require ADMIN authorization");
        }
        return user;
    }

    private void validateRequest(ModelDeploymentRequest request) {
        if (request == null || request.actionId() == null) {
            throw new IllegalArgumentException("actionId is required");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new IllegalArgumentException("A promotion or rollback reason is required");
        }
    }

    private ModelDeploymentEvent requireReplay(
            ModelDeploymentEvent event,
            String expectedAction,
            String expectedModelVersion
    ) {
        if (!expectedAction.equals(event.deploymentAction())
                || !Objects.equals(expectedModelVersion, event.activatedModelVersion())) {
            throw new IllegalArgumentException("actionId was already used for a different deployment action");
        }
        return event;
    }

    private void verifyCompatible(AmlModelRegistryEntry first, AmlModelRegistryEntry second) {
        if (!first.modelType().equals(second.modelType())
                || !first.featureVersion().equals(second.featureVersion())
                || !Objects.equals(first.modelSegment(), second.modelSegment())) {
            throw new IllegalStateException("Champion and candidate deployment contracts are incompatible");
        }
    }

    private void verifyArtifact(AmlModelRegistryEntry model) {
        try {
            String checksum = checksumService.sha256Bundle(Path.of(model.artifactPath()));
            if (!checksum.equalsIgnoreCase(model.artifactChecksum())) {
                throw new IllegalStateException("Model artifact checksum changed after registration");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to verify model artifact before deployment", exception);
        }
    }

    private ModelDeploymentEvent event(
            ModelDeploymentRequest request,
            String action,
            AmlModelRegistryEntry scope,
            String previous,
            String activated,
            String actor
    ) {
        return new ModelDeploymentEvent(
                UUID.randomUUID(), request.actionId(), action, scope.modelType(), scope.modelSegment(),
                previous, activated, request.reason().trim(), actor, Instant.now(clock)
        );
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }
}
