package com.ftd.fraud_transaction_detector.aml.validation.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlModelRegistryEntry;
import com.ftd.fraud_transaction_detector.aml.training.infrastructure.AmlModelRegistryRepository;
import com.ftd.fraud_transaction_detector.aml.validation.api.ValidateCandidateRequest;
import com.ftd.fraud_transaction_detector.aml.validation.domain.ChallengerMetrics;
import com.ftd.fraud_transaction_detector.aml.validation.domain.ModelValidationReport;
import com.ftd.fraud_transaction_detector.aml.validation.infrastructure.ModelValidationRepository;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ModelValidationService {

    private final AmlModelRegistryRepository registryRepository;
    private final ModelValidationRepository validationRepository;
    private final AppConfigService configService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public ModelValidationService(
            AmlModelRegistryRepository registryRepository,
            ModelValidationRepository validationRepository,
            AppConfigService configService,
            ObjectMapper objectMapper
    ) {
        this(registryRepository, validationRepository, configService, objectMapper, Clock.systemUTC());
    }

    ModelValidationService(
            AmlModelRegistryRepository registryRepository,
            ModelValidationRepository validationRepository,
            AppConfigService configService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.registryRepository = registryRepository;
        this.validationRepository = validationRepository;
        this.configService = configService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public ModelValidationReport validate(String modelVersion, ValidateCandidateRequest request) {
        AmlModelRegistryEntry model = registryRepository.findRequired(modelVersion);
        if (!"HALF_SPACE_TREES".equals(model.modelType())) {
            throw new IllegalArgumentException("Phase 10 validation currently supports HALF_SPACE_TREES candidates");
        }
        if (!List.of("CANDIDATE", "VALIDATED").contains(model.status())) {
            throw new IllegalStateException("Only CANDIDATE or VALIDATED models can be evaluated");
        }
        Instant now = Instant.now(clock);
        Instant from = request == null || request.windowStartedAt() == null ? model.createdAt() : request.windowStartedAt();
        Instant to = request == null || request.windowEndedAt() == null ? now : request.windowEndedAt();
        if (from.isAfter(to) || to.isAfter(now)) {
            throw new IllegalArgumentException("Validation window must be ordered and cannot end in the future");
        }
        double threshold = threshold(model);
        ChallengerMetrics metrics = validationRepository.calculate(modelVersion, threshold, from, to);
        Decision decision = decide(metrics);
        ModelValidationReport report = new ModelValidationReport(
                UUID.randomUUID(), modelVersion, "CURRENT_PRODUCTION_ENSEMBLE",
                from, to, metrics, decision.status, decision.reason,
                actor(request), now
        );
        try {
            validationRepository.save(report, objectMapper.writeValueAsString(metrics));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist model validation report", exception);
        }
        if ("PASSED".equals(report.validationStatus())) {
            validationRepository.markValidated(modelVersion, metrics);
        }
        return report;
    }

    public List<ModelValidationReport> reports(String modelVersion) {
        registryRepository.findRequired(modelVersion);
        return validationRepository.findByModelVersion(modelVersion);
    }

    private Decision decide(ChallengerMetrics metrics) {
        if (metrics.sampleCount() < configService.getValidationMinRows()) {
            return new Decision("INSUFFICIENT_DATA",
                    "Requires at least " + configService.getValidationMinRows() + " silent predictions");
        }
        List<String> failures = new ArrayList<>();
        if (metrics.candidateAnomalyRate() < configService.getValidationMinAnomalyRate()) {
            failures.add("candidate anomaly rate is below the configured minimum");
        }
        if (metrics.candidateAnomalyRate() > configService.getValidationMaxAnomalyRate()) {
            failures.add("candidate anomaly rate exceeds the configured maximum");
        }
        if (metrics.dailyAnomalyRateStandardDeviation() != null
                && metrics.dailyAnomalyRateStandardDeviation() > configService.getValidationMaxDailyRateStddev()) {
            failures.add("daily anomaly-rate stability exceeds the configured limit");
        }
        if (metrics.scoreP50() == null || metrics.scoreP99() == null || metrics.scoreP99() <= metrics.scoreP50()) {
            failures.add("candidate scores do not provide measurable upper-tail separation");
        }
        if (metrics.reviewedOverlapCount() >= configService.getValidationMinReviewedAlerts()
                && (metrics.reviewedPrecision() == null
                || metrics.reviewedPrecision() < configService.getValidationMinReviewedPrecision())) {
            failures.add("reviewed suspicious precision is below the configured minimum");
        }
        return failures.isEmpty()
                ? new Decision("PASSED", null)
                : new Decision("FAILED", String.join("; ", failures));
    }

    private double threshold(AmlModelRegistryEntry model) {
        try {
            JsonNode metrics = objectMapper.readTree(model.metricsJson());
            double threshold = metrics.path("threshold").asDouble(Double.NaN);
            if (!Double.isFinite(threshold)) throw new IllegalArgumentException();
            return threshold;
        } catch (Exception exception) {
            throw new IllegalStateException("Candidate registry metrics do not contain a valid threshold", exception);
        }
    }

    private String actor(ValidateCandidateRequest request) {
        return request == null || request.validatedBy() == null || request.validatedBy().isBlank()
                ? "model-validation-service" : request.validatedBy().trim();
    }

    private record Decision(String status, String reason) {}
}
