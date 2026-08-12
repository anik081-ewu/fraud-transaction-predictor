package com.ftd.fraud_transaction_detector.aml.validation.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.aml.risk.domain.RiskPolicyRepository;
import com.ftd.fraud_transaction_detector.aml.validation.api.SyntheticScenarioLabelRequest;
import com.ftd.fraud_transaction_detector.aml.validation.api.ValidateLayeredShadowRequest;
import com.ftd.fraud_transaction_detector.aml.validation.domain.LayeredShadowValidationMetrics;
import com.ftd.fraud_transaction_detector.aml.validation.domain.LayeredShadowValidationReport;
import com.ftd.fraud_transaction_detector.aml.validation.domain.SyntheticScenarioLabel;
import com.ftd.fraud_transaction_detector.aml.validation.infrastructure.LayeredShadowValidationRepository;
import com.ftd.fraud_transaction_detector.config.service.AppConfigService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class LayeredShadowValidationService {

    private static final Duration DEFAULT_WINDOW = Duration.ofDays(30);

    private final LayeredShadowValidationRepository repository;
    private final RiskPolicyRepository riskPolicyRepository;
    private final AppConfigService configService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public LayeredShadowValidationService(
            LayeredShadowValidationRepository repository,
            RiskPolicyRepository riskPolicyRepository,
            AppConfigService configService,
            ObjectMapper objectMapper
    ) {
        this(repository, riskPolicyRepository, configService, objectMapper, Clock.systemUTC());
    }

    LayeredShadowValidationService(
            LayeredShadowValidationRepository repository,
            RiskPolicyRepository riskPolicyRepository,
            AppConfigService configService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.repository = repository;
        this.riskPolicyRepository = riskPolicyRepository;
        this.configService = configService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public LayeredShadowValidationReport validate(ValidateLayeredShadowRequest request) {
        Instant now = Instant.now(clock);
        Instant to = request == null || request.windowEndedAt() == null ? now : request.windowEndedAt();
        Instant from = request == null || request.windowStartedAt() == null
                ? to.minus(DEFAULT_WINDOW) : request.windowStartedAt();
        if (from.isAfter(to) || to.isAfter(now)) {
            throw new IllegalArgumentException("Validation window must be ordered and cannot end in the future");
        }
        String peerGroup = normalize(request == null ? null : request.peerGroupCode());
        String requestedPolicy = normalize(request == null ? null : request.riskPolicyVersion());
        String policyVersion = requestedPolicy == null
                ? riskPolicyRepository.findActive(peerGroup).version() : requestedPolicy;
        LayeredShadowValidationMetrics metrics = repository.calculate(policyVersion, peerGroup, from, to);
        Decision decision = decide(metrics);
        LayeredShadowValidationReport report = new LayeredShadowValidationReport(
                UUID.randomUUID(), policyVersion, peerGroup, from, to, metrics,
                decision.status(), decision.blockingReasons(), warnings(metrics),
                actor(request), now
        );
        try {
            repository.save(
                    report,
                    objectMapper.writeValueAsString(report.blockingReasons()),
                    objectMapper.writeValueAsString(report.warnings()),
                    objectMapper.writeValueAsString(metrics)
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist layered shadow validation", exception);
        }
        return report;
    }

    public List<LayeredShadowValidationReport> reports() {
        return repository.findRecentStored().stream().map(stored -> {
            try {
                return new LayeredShadowValidationReport(
                        stored.validationId(), stored.riskPolicyVersion(), stored.peerGroupCode(),
                        stored.windowStartedAt(), stored.windowEndedAt(),
                        objectMapper.readValue(stored.metricsJson(), LayeredShadowValidationMetrics.class),
                        stored.validationStatus(),
                        objectMapper.readValue(stored.blockingReasonsJson(), new TypeReference<List<String>>() {}),
                        objectMapper.readValue(stored.warningsJson(), new TypeReference<List<String>>() {}),
                        stored.validatedBy(), stored.validatedAt()
                );
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to read layered shadow validation history", exception);
            }
        }).toList();
    }

    @Transactional
    public SyntheticScenarioLabel label(SyntheticScenarioLabelRequest request) {
        String transactionId = request.transactionId().trim();
        if (!repository.shadowPredictionExists(transactionId)) {
            throw new IllegalArgumentException("No layered shadow prediction exists for transaction " + transactionId);
        }
        return repository.saveScenarioLabel(new SyntheticScenarioLabel(
                UUID.randomUUID(), transactionId, request.scenarioCode().trim().toUpperCase(),
                request.expectedSuspicious(), request.labeledBy().trim(), Instant.now(clock)
        ));
    }

    private Decision decide(LayeredShadowValidationMetrics metrics) {
        Thresholds thresholds = thresholds();
        List<String> insufficient = new ArrayList<>();
        if (metrics.sampleCount() < thresholds.minRows()) {
            insufficient.add("Requires at least " + thresholds.minRows() + " shadow predictions");
        }
        if (metrics.observationDays() < thresholds.minObservationDays()) {
            insufficient.add("Requires at least " + thresholds.minObservationDays() + " observation days");
        }
        if (metrics.legacyAlertCount() < thresholds.minLegacyAlerts()) {
            insufficient.add("Requires at least " + thresholds.minLegacyAlerts() + " legacy alerts");
        }
        if (metrics.syntheticExpectedSuspiciousCount() < thresholds.minSyntheticScenarios()) {
            insufficient.add("Requires at least " + thresholds.minSyntheticScenarios()
                    + " expected-positive synthetic scenarios");
        }
        if (metrics.reviewedLayeredAlertCount() < thresholds.minReviewedAlerts()) {
            insufficient.add("Requires at least " + thresholds.minReviewedAlerts()
                    + " reviewed layered alerts");
        }
        if (metrics.incrementalUpdateCount() == 0) {
            insufficient.add("Requires at least one completed incremental update in the validation window");
        }
        if (!insufficient.isEmpty()) return new Decision("INSUFFICIENT_DATA", insufficient);

        List<String> failures = new ArrayList<>();
        failAbove(failures, metrics.layeredAlertRate(), thresholds.maxAlertRate(),
                "Layered alert rate exceeds the configured maximum");
        failAbove(failures, metrics.alertVolumeChangeRate(), thresholds.maxAlertVolumeIncrease(),
                "Layered alert-volume increase exceeds the configured maximum");
        failBelow(failures, metrics.topRiskOverlapRate(), thresholds.minTopRiskOverlap(),
                "Top-risk overlap is below the configured minimum");
        failAbove(failures, metrics.dailyLayeredAlertRateStandardDeviation(),
                thresholds.maxDailyRateStddev(),
                "Daily layered alert-rate stability exceeds the configured limit");
        failAbove(failures, metrics.maxSegmentDailyAlertRateStandardDeviation(),
                thresholds.maxSegmentDailyStddev(),
                "At least one segment exceeds the configured stability limit");
        failBelow(failures, metrics.syntheticScenarioRecall(), thresholds.minSyntheticRecall(),
                "Synthetic AML scenario recall is below the configured minimum");
        failAbove(failures, metrics.reviewedFalsePositiveRate(),
                thresholds.maxReviewedFalsePositiveRate(),
                "Reviewed false-positive rate exceeds the configured maximum");
        failAbove(failures, metrics.predictionLatencyP95Ms(), thresholds.maxP95LatencyMs(),
                "Prediction p95 latency exceeds the configured maximum");
        failBelow(failures, metrics.hstAvailabilityRate(), thresholds.minModelAvailability(),
                "Half-Space Trees score availability is below the configured minimum");
        failBelow(failures, metrics.onlineOcSvmAvailabilityRate(), thresholds.minModelAvailability(),
                "Online One-Class SVM score availability is below the configured minimum");
        if (metrics.distinctHstModelVersionCount() != 1 || metrics.hstModelVersion() == null) {
            failures.add("Validation window must contain exactly one Half-Space Trees model version");
        }
        if (metrics.distinctOnlineOcSvmModelVersionCount() != 1 || metrics.onlineOcSvmModelVersion() == null) {
            failures.add("Validation window must contain exactly one Online One-Class SVM model version");
        }
        failAbove(failures, metrics.averageIncrementalUpdateMs(),
                thresholds.maxAverageIncrementalUpdateMs(),
                "Average incremental update time exceeds the configured maximum");
        if (metrics.layeredScoreP50() == null || metrics.layeredScoreP99() == null
                || metrics.layeredScoreP99() <= metrics.layeredScoreP50()) {
            failures.add("Layered scores do not provide measurable upper-tail separation");
        }
        return failures.isEmpty() ? new Decision("PASSED", List.of()) : new Decision("FAILED", failures);
    }

    private List<String> warnings(LayeredShadowValidationMetrics metrics) {
        List<String> warnings = new ArrayList<>();
        warnings.add("Reviewed precision and false-positive rate are selection-biased because layered-only alerts are not yet reviewed in production");
        warnings.add("Synthetic recall measures labeled expected-positive scenarios and is not population recall");
        if (metrics.segmentMetrics().stream().anyMatch(segment -> "UNCLASSIFIED".equals(segment.peerGroupCode()))) {
            warnings.add("Some historical shadow rows have no peer-group classification");
        }
        return warnings;
    }

    private void failAbove(List<String> failures, Double value, double maximum, String reason) {
        if (value == null || !Double.isFinite(value) || value > maximum) failures.add(reason);
    }

    private void failBelow(List<String> failures, Double value, double minimum, String reason) {
        if (value == null || !Double.isFinite(value) || value < minimum) failures.add(reason);
    }

    private String actor(ValidateLayeredShadowRequest request) {
        String actor = request == null ? null : normalize(request.validatedBy());
        return actor == null ? "layered-shadow-validation-service" : actor;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Thresholds thresholds() {
        return new Thresholds(
                configService.getLayeredValidationMinRows(),
                configService.getLayeredValidationMinObservationDays(),
                configService.getLayeredValidationMinLegacyAlerts(),
                configService.getLayeredValidationMaxAlertRate(),
                configService.getLayeredValidationMaxAlertVolumeIncrease(),
                configService.getLayeredValidationMinTopRiskOverlap(),
                configService.getLayeredValidationMaxDailyRateStddev(),
                configService.getLayeredValidationMaxSegmentDailyStddev(),
                configService.getLayeredValidationMinSyntheticScenarios(),
                configService.getLayeredValidationMinSyntheticRecall(),
                configService.getLayeredValidationMinReviewedAlerts(),
                configService.getLayeredValidationMaxReviewedFalsePositiveRate(),
                configService.getLayeredValidationMaxP95LatencyMs(),
                configService.getLayeredValidationMinModelAvailability(),
                configService.getLayeredValidationMaxAverageIncrementalUpdateMs()
        );
    }

    private record Decision(String status, List<String> blockingReasons) {
    }

    private record Thresholds(
            int minRows,
            int minObservationDays,
            int minLegacyAlerts,
            double maxAlertRate,
            double maxAlertVolumeIncrease,
            double minTopRiskOverlap,
            double maxDailyRateStddev,
            double maxSegmentDailyStddev,
            int minSyntheticScenarios,
            double minSyntheticRecall,
            int minReviewedAlerts,
            double maxReviewedFalsePositiveRate,
            double maxP95LatencyMs,
            double minModelAvailability,
            double maxAverageIncrementalUpdateMs
    ) {
        private Thresholds {
            if (minRows <= 0 || minObservationDays <= 0 || minLegacyAlerts <= 0
                    || minSyntheticScenarios <= 0 || minReviewedAlerts <= 0) {
                throw new IllegalStateException("Layered validation minimum evidence gates must be positive");
            }
            bounded(maxAlertRate, "maxAlertRate");
            bounded(minTopRiskOverlap, "minTopRiskOverlap");
            bounded(maxDailyRateStddev, "maxDailyRateStddev");
            bounded(maxSegmentDailyStddev, "maxSegmentDailyStddev");
            bounded(minSyntheticRecall, "minSyntheticRecall");
            bounded(maxReviewedFalsePositiveRate, "maxReviewedFalsePositiveRate");
            bounded(minModelAvailability, "minModelAvailability");
            if (!Double.isFinite(maxAlertVolumeIncrease) || maxAlertVolumeIncrease < 0.0
                    || !Double.isFinite(maxP95LatencyMs) || maxP95LatencyMs <= 0.0
                    || !Double.isFinite(maxAverageIncrementalUpdateMs) || maxAverageIncrementalUpdateMs <= 0.0) {
                throw new IllegalStateException("Layered validation volume and duration gates are invalid");
            }
        }

        private static void bounded(double value, String name) {
            if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
                throw new IllegalStateException(name + " must be between 0.0 and 1.0");
            }
        }
    }
}
