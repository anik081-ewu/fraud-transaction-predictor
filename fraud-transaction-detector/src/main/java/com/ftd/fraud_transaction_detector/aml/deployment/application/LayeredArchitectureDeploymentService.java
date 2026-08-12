package com.ftd.fraud_transaction_detector.aml.deployment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.aml.deployment.api.PromoteLayeredArchitectureRequest;
import com.ftd.fraud_transaction_detector.aml.deployment.api.RollbackLayeredArchitectureRequest;
import com.ftd.fraud_transaction_detector.aml.deployment.domain.LayeredDeploymentEvent;
import com.ftd.fraud_transaction_detector.aml.deployment.domain.LayeredDeploymentPointer;
import com.ftd.fraud_transaction_detector.aml.deployment.infrastructure.LayeredArchitectureDeploymentRepository;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlModelRegistryEntry;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.AmlModelRegistryRepository;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.FileChecksumService;
import com.ftd.fraud_transaction_detector.aml.validation.domain.LayeredShadowValidationMetrics;
import com.ftd.fraud_transaction_detector.aml.validation.infrastructure.LayeredShadowValidationRepository;
import com.ftd.fraud_transaction_detector.auth.dto.UserResponse;
import com.ftd.fraud_transaction_detector.auth.service.AuthService;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class LayeredArchitectureDeploymentService {

    private static final List<String> AUTHORIZED_ROLES = List.of("ADMIN", "AML_ADMIN");
    private static final String ACTIVE = "LAYERED_ACTIVE";
    private static final String FALLBACK = "ISOLATION_FOREST_FALLBACK";

    private final LayeredArchitectureDeploymentRepository deploymentRepository;
    private final LayeredShadowValidationRepository validationRepository;
    private final AmlModelRegistryRepository modelRegistryRepository;
    private final FileChecksumService checksumService;
    private final AuthService authService;
    private final AppConfigService configService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public LayeredArchitectureDeploymentService(
            LayeredArchitectureDeploymentRepository deploymentRepository,
            LayeredShadowValidationRepository validationRepository,
            AmlModelRegistryRepository modelRegistryRepository,
            FileChecksumService checksumService,
            AuthService authService,
            AppConfigService configService,
            ObjectMapper objectMapper
    ) {
        this(deploymentRepository, validationRepository, modelRegistryRepository, checksumService,
                authService, configService, objectMapper, Clock.systemUTC());
    }

    LayeredArchitectureDeploymentService(
            LayeredArchitectureDeploymentRepository deploymentRepository,
            LayeredShadowValidationRepository validationRepository,
            AmlModelRegistryRepository modelRegistryRepository,
            FileChecksumService checksumService,
            AuthService authService,
            AppConfigService configService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.deploymentRepository = deploymentRepository;
        this.validationRepository = validationRepository;
        this.modelRegistryRepository = modelRegistryRepository;
        this.checksumService = checksumService;
        this.authService = authService;
        this.configService = configService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public LayeredDeploymentEvent promote(
            PromoteLayeredArchitectureRequest request,
            String authorizationHeader
    ) {
        UserResponse actor = authorizedUser(authorizationHeader);
        validatePromotionRequest(request);
        LayeredDeploymentEvent replay = deploymentRepository.findEvent(request.actionId()).orElse(null);
        if (replay != null) return requirePromotionReplay(replay, request);
        String peerGroup = normalize(request.peerGroupCode());
        var validation = validationRepository.findStored(request.validationId())
                .orElseThrow(() -> new IllegalArgumentException("Layered validation report was not found"));
        if (!"PASSED".equals(validation.validationStatus())) {
            throw new IllegalStateException("Only a PASSED layered validation can authorize promotion");
        }
        if (!Objects.equals(peerGroup, normalize(validation.peerGroupCode()))) {
            throw new IllegalStateException("Promotion requires a passing validation for the exact peer group");
        }
        Instant oldestAllowed = Instant.now(clock).minus(
                configService.getLayeredDeploymentMaxValidationAgeDays(), ChronoUnit.DAYS
        );
        if (validation.validatedAt().isBefore(oldestAllowed)) {
            throw new IllegalStateException("Layered validation is older than the configured promotion limit");
        }

        LayeredShadowValidationMetrics metrics = metrics(validation.metricsJson());
        requireVersionLock(metrics);
        AmlModelRegistryEntry hst = modelRegistryRepository.findRequired(metrics.hstModelVersion());
        AmlModelRegistryEntry ocsvm = modelRegistryRepository.findRequired(metrics.onlineOcSvmModelVersion());
        verifyModel(hst, "HALF_SPACE_TREES", peerGroup);
        verifyModel(ocsvm, "ONLINE_ONE_CLASS_SVM", peerGroup);
        if (!hst.featureVersion().equals(ocsvm.featureVersion())) {
            throw new IllegalStateException("Validated HST and Online OCSVM feature versions are incompatible");
        }
        verifyArtifact(hst);
        verifyArtifact(ocsvm);

        LayeredDeploymentPointer current = deploymentRepository.lockPointer(peerGroup).orElse(null);
        String action = action(current, request.canaryPercentage(), validation.validationId(), validation.riskPolicyVersion());
        long pointerVersion = current == null ? 1 : current.pointerVersion() + 1;
        Instant now = Instant.now(clock);
        LayeredDeploymentPointer activated = new LayeredDeploymentPointer(
                peerGroup, ACTIVE, validation.riskPolicyVersion(), hst.modelVersion(), ocsvm.modelVersion(),
                validation.validationId(), request.canaryPercentage(), pointerVersion, actor.username(), now
        );
        LayeredDeploymentEvent event = new LayeredDeploymentEvent(
                UUID.randomUUID(), request.actionId(), action, peerGroup,
                current == null ? null : current.deploymentMode(), ACTIVE,
                validation.riskPolicyVersion(), hst.modelVersion(), ocsvm.modelVersion(),
                validation.validationId(), current == null ? null : current.canaryPercentage(),
                request.canaryPercentage(), request.reason().trim(), actor.username(), now
        );
        deploymentRepository.promote(activated, current, event);
        return event;
    }

    @Transactional
    public LayeredDeploymentEvent rollback(
            RollbackLayeredArchitectureRequest request,
            String authorizationHeader
    ) {
        UserResponse actor = authorizedUser(authorizationHeader);
        validateRollbackRequest(request);
        LayeredDeploymentEvent replay = deploymentRepository.findEvent(request.actionId()).orElse(null);
        if (replay != null) return requireRollbackReplay(replay, request);
        String peerGroup = normalize(request.peerGroupCode());
        LayeredDeploymentPointer current = deploymentRepository.lockPointer(peerGroup)
                .orElseThrow(() -> new IllegalStateException("No layered deployment exists for this peer group"));
        if (!ACTIVE.equals(current.deploymentMode())) {
            throw new IllegalStateException("Peer group is already using the Isolation Forest fallback");
        }
        Instant now = Instant.now(clock);
        LayeredDeploymentPointer fallback = new LayeredDeploymentPointer(
                peerGroup, FALLBACK, current.riskPolicyVersion(), current.hstModelVersion(),
                current.onlineOcSvmModelVersion(), current.validationId(), 0,
                current.pointerVersion() + 1, actor.username(), now
        );
        LayeredDeploymentEvent event = new LayeredDeploymentEvent(
                UUID.randomUUID(), request.actionId(), "ROLLBACK", peerGroup,
                ACTIVE, FALLBACK, current.riskPolicyVersion(), current.hstModelVersion(),
                current.onlineOcSvmModelVersion(), current.validationId(), current.canaryPercentage(),
                0, request.reason().trim(), actor.username(), now
        );
        deploymentRepository.rollback(current, fallback, event);
        return event;
    }

    public List<LayeredDeploymentPointer> pointers() {
        return deploymentRepository.pointers();
    }

    public List<LayeredDeploymentEvent> history(String peerGroupCode) {
        return deploymentRepository.history(normalize(peerGroupCode));
    }

    private String action(
            LayeredDeploymentPointer current,
            int requestedCanary,
            UUID validationId,
            String policyVersion
    ) {
        if (current == null || FALLBACK.equals(current.deploymentMode())) return "PROMOTION";
        if (!Objects.equals(current.validationId(), validationId)
                || !Objects.equals(current.riskPolicyVersion(), policyVersion)) {
            throw new IllegalStateException("Changing validation or risk policy requires rollback before a new promotion");
        }
        if (requestedCanary <= current.canaryPercentage()) {
            throw new IllegalStateException("Canary expansion must increase the active percentage");
        }
        return "CANARY_EXPANSION";
    }

    private void requireVersionLock(LayeredShadowValidationMetrics metrics) {
        if (metrics.distinctHstModelVersionCount() != 1 || metrics.hstModelVersion() == null
                || metrics.distinctOnlineOcSvmModelVersionCount() != 1 || metrics.onlineOcSvmModelVersion() == null) {
            throw new IllegalStateException("Passing validation does not lock exact production model versions");
        }
    }

    private void verifyModel(AmlModelRegistryEntry model, String expectedType, String peerGroup) {
        if (!expectedType.equals(model.modelType())) {
            throw new IllegalStateException("Validated model type does not match " + expectedType);
        }
        if (!List.of("CANDIDATE", "VALIDATED", "CHAMPION").contains(model.status())) {
            throw new IllegalStateException("Validated model is not deployment eligible: " + model.modelVersion());
        }
        if (model.modelSegment() != null && !peerGroup.equals(normalize(model.modelSegment()))) {
            throw new IllegalStateException("Validated model is incompatible with peer group " + peerGroup);
        }
    }

    private void verifyArtifact(AmlModelRegistryEntry model) {
        try {
            String checksum = checksumService.sha256Bundle(Path.of(model.artifactPath()));
            if (!checksum.equalsIgnoreCase(model.artifactChecksum())) {
                throw new IllegalStateException("Model artifact checksum changed after validation");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to verify model artifact before layered promotion", exception);
        }
    }

    private LayeredShadowValidationMetrics metrics(String json) {
        try {
            return objectMapper.readValue(json, LayeredShadowValidationMetrics.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Layered validation metrics cannot be read", exception);
        }
    }

    private UserResponse authorizedUser(String authorizationHeader) {
        UserResponse user = authService.getCurrentUser(authorizationHeader);
        if (!user.active() || user.roleName() == null
                || !AUTHORIZED_ROLES.contains(user.roleName().trim().toUpperCase())) {
            throw new IllegalArgumentException("Layered promotion and rollback require ADMIN authorization");
        }
        return user;
    }

    private LayeredDeploymentEvent requirePromotionReplay(
            LayeredDeploymentEvent event,
            PromoteLayeredArchitectureRequest request
    ) {
        if (!("PROMOTION".equals(event.deploymentAction())
                || "CANARY_EXPANSION".equals(event.deploymentAction()))
                || !event.actionId().equals(request.actionId())
                || !event.validationId().equals(request.validationId())
                || event.activatedCanaryPercentage() != request.canaryPercentage()
                || !event.peerGroupCode().equals(normalize(request.peerGroupCode()))) {
            throw new IllegalArgumentException("actionId was already used for a different layered deployment action");
        }
        return event;
    }

    private LayeredDeploymentEvent requireRollbackReplay(
            LayeredDeploymentEvent event,
            RollbackLayeredArchitectureRequest request
    ) {
        if (!"ROLLBACK".equals(event.deploymentAction())
                || !event.actionId().equals(request.actionId())
                || !event.peerGroupCode().equals(normalize(request.peerGroupCode()))) {
            throw new IllegalArgumentException("actionId was already used for a different layered deployment action");
        }
        return event;
    }

    private void validatePromotionRequest(PromoteLayeredArchitectureRequest request) {
        if (request == null || request.actionId() == null || request.validationId() == null
                || request.peerGroupCode() == null || request.peerGroupCode().isBlank()
                || request.reason() == null || request.reason().isBlank()
                || request.canaryPercentage() < 1 || request.canaryPercentage() > 100) {
            throw new IllegalArgumentException("Valid action, validation, peer group, canary, and reason are required");
        }
    }

    private void validateRollbackRequest(RollbackLayeredArchitectureRequest request) {
        if (request == null || request.actionId() == null
                || request.peerGroupCode() == null || request.peerGroupCode().isBlank()
                || request.reason() == null || request.reason().isBlank()) {
            throw new IllegalArgumentException("Valid action, peer group, and rollback reason are required");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }
}
