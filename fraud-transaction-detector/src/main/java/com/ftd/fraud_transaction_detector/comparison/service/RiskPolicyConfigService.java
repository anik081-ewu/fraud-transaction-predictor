package com.ftd.fraud_transaction_detector.comparison.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.aml.risk.domain.RiskPolicy;
import com.ftd.fraud_transaction_detector.aml.risk.infrastructure.AppConfigRiskPolicyRepository;
import com.ftd.fraud_transaction_detector.comparison.dto.RiskPolicyConfigResponse;
import com.ftd.fraud_transaction_detector.comparison.dto.RiskPolicyModelConfigRequest;
import com.ftd.fraud_transaction_detector.comparison.dto.RiskPolicyModelConfigResponse;
import com.ftd.fraud_transaction_detector.comparison.dto.RiskPolicyConfigUpdateRequest;
import com.ftd.fraud_transaction_detector.config.entity.AppConfig;
import com.ftd.fraud_transaction_detector.config.repo.AppConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RiskPolicyConfigService {

    private static final String MODEL_ALLOCATIONS_JSON = "aml.risk.ml_model_allocations_json";
    private static final DateTimeFormatter VERSION_FORMAT = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmssSSS")
            .withZone(ZoneOffset.UTC);
    private static final TypeReference<List<StoredModelAllocation>> MODEL_LIST = new TypeReference<>() {};

    private static final Map<String, Definition> DEFINITIONS = definitions();
    private static final String SYSTEM_LEARNING_MODE = "system.learning_mode";
    private static final Map<String, SupportedModel> UNSUPERVISED_MODELS = unsupervisedModels();
    private static final Map<String, SupportedModel> SUPERVISED_MODELS = supervisedModels();

    private final AppConfigRepository appConfigRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public RiskPolicyConfigService(AppConfigRepository appConfigRepository, ObjectMapper objectMapper) {
        this(appConfigRepository, objectMapper, Clock.systemUTC());
    }

    RiskPolicyConfigService(AppConfigRepository appConfigRepository, ObjectMapper objectMapper, Clock clock) {
        this.appConfigRepository = appConfigRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RiskPolicyConfigResponse get() {
        return response();
    }

    @Transactional
    public RiskPolicyConfigResponse update(RiskPolicyConfigUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Risk policy values are required");
        }
        Instant now = clock.instant();
        String version = "AML_RISK_POLICY_" + VERSION_FORMAT.format(now);
        Map<String, StoredModelAllocation> modelAllocations = validatedModels(request.models());
        new RiskPolicy(
                version,
                request.customerBehaviourWeight(),
                request.peerBehaviourWeight(),
                request.mlEnsembleWeight(),
                request.rulesWeight(),
                request.lowRiskThreshold(),
                request.mediumRiskThreshold(),
                request.highRiskThreshold()
        );

        save(AppConfigRiskPolicyRepository.VERSION, version, now);
        save(AppConfigRiskPolicyRepository.CUSTOMER_WEIGHT, decimal(request.customerBehaviourWeight()), now);
        save(AppConfigRiskPolicyRepository.PEER_WEIGHT, decimal(request.peerBehaviourWeight()), now);
        save(AppConfigRiskPolicyRepository.ML_ENSEMBLE_WEIGHT, decimal(request.mlEnsembleWeight()), now);
        save(AppConfigRiskPolicyRepository.RULES_WEIGHT, decimal(request.rulesWeight()), now);
        save(MODEL_ALLOCATIONS_JSON, json(modelAllocations.values().stream()
                .sorted(Comparator.comparing(StoredModelAllocation::modelKey))
                .toList()), now);
        save(AppConfigRiskPolicyRepository.LOW_THRESHOLD, decimal(request.lowRiskThreshold()), now);
        save(AppConfigRiskPolicyRepository.MEDIUM_THRESHOLD, decimal(request.mediumRiskThreshold()), now);
        save(AppConfigRiskPolicyRepository.HIGH_THRESHOLD, decimal(request.highRiskThreshold()), now);
        RiskPolicyConfigUpdateRequest.CustomerBehaviourSubWeightsRequest cbSub =
                request.customerBehaviourSubWeights() != null
                        ? request.customerBehaviourSubWeights()
                        : new RiskPolicyConfigUpdateRequest.CustomerBehaviourSubWeightsRequest(0.55, 0.20, 0.12, 0.08, 0.05);
        double cbTotal = cbSub.amount() + cbSub.novelty() + cbSub.frequency() + cbSub.timeGap() + cbSub.unusualHour();
        if (Math.abs(cbTotal - 1.0) > 0.000001) {
            throw new IllegalArgumentException("Customer behaviour sub-weights must total exactly 1.0");
        }
        save(AppConfigRiskPolicyRepository.CB_AMOUNT_WEIGHT, decimal(cbSub.amount()), now);
        save(AppConfigRiskPolicyRepository.CB_NOVELTY_WEIGHT, decimal(cbSub.novelty()), now);
        save(AppConfigRiskPolicyRepository.CB_FREQUENCY_WEIGHT, decimal(cbSub.frequency()), now);
        save(AppConfigRiskPolicyRepository.CB_TIME_GAP_WEIGHT, decimal(cbSub.timeGap()), now);
        save(AppConfigRiskPolicyRepository.CB_UNUSUAL_HOUR_WEIGHT, decimal(cbSub.unusualHour()), now);
        RiskPolicyConfigUpdateRequest.PeerBehaviourSubWeightsRequest pbSub =
                request.peerBehaviourSubWeights() != null
                        ? request.peerBehaviourSubWeights()
                        : new RiskPolicyConfigUpdateRequest.PeerBehaviourSubWeightsRequest(0.60, 0.25, 0.15);
        double pbTotal = pbSub.amount() + pbSub.frequency() + pbSub.expectedTurnover();
        if (Math.abs(pbTotal - 1.0) > 0.000001) {
            throw new IllegalArgumentException("Peer behaviour sub-weights must total exactly 1.0");
        }
        save(AppConfigRiskPolicyRepository.PB_AMOUNT_WEIGHT, decimal(pbSub.amount()), now);
        save(AppConfigRiskPolicyRepository.PB_FREQUENCY_WEIGHT, decimal(pbSub.frequency()), now);
        save(AppConfigRiskPolicyRepository.PB_EXPECTED_TURNOVER_WEIGHT, decimal(pbSub.expectedTurnover()), now);
        RiskPolicyConfigUpdateRequest.AmlRuleThresholdsRequest rules =
                request.amlRuleThresholds() != null
                        ? request.amlRuleThresholds()
                        : new RiskPolicyConfigUpdateRequest.AmlRuleThresholdsRequest(
                                10_000.0, 3, 5, 10, 4, 4, 4.0, 8.0, 0.80, 0.50);
        try {
            new com.ftd.fraud_transaction_detector.aml.rules.engine.DeterministicAmlRulePolicy(
                    "DETERMINISTIC_AML_RULES_V1",
                    rules.reportingThreshold(), rules.structuringCount24h(),
                    rules.rapidTxCount10m(), rules.highTxCount1h(),
                    rules.multiBeneficiaryCount1h(), rules.repeatedAmountCount24h(),
                    rules.highCustomerAmountRatio(), rules.extremeCustomerAmountRatio(),
                    rules.highBalanceRatio(), rules.highExpectedTurnoverRatio());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("AML rule thresholds are invalid: " + e.getMessage(), e);
        }
        save(AppConfigRiskPolicyRepository.RULES_REPORTING_THRESHOLD, decimal(rules.reportingThreshold()), now);
        save(AppConfigRiskPolicyRepository.RULES_STRUCTURING_COUNT_24H, Integer.toString(rules.structuringCount24h()), now);
        save(AppConfigRiskPolicyRepository.RULES_RAPID_TX_COUNT_10M, Integer.toString(rules.rapidTxCount10m()), now);
        save(AppConfigRiskPolicyRepository.RULES_HIGH_TX_COUNT_1H, Integer.toString(rules.highTxCount1h()), now);
        save(AppConfigRiskPolicyRepository.RULES_MULTI_BENEFICIARY_COUNT_1H, Integer.toString(rules.multiBeneficiaryCount1h()), now);
        save(AppConfigRiskPolicyRepository.RULES_REPEATED_AMOUNT_COUNT_24H, Integer.toString(rules.repeatedAmountCount24h()), now);
        save(AppConfigRiskPolicyRepository.RULES_HIGH_CUSTOMER_AMOUNT_RATIO, decimal(rules.highCustomerAmountRatio()), now);
        save(AppConfigRiskPolicyRepository.RULES_EXTREME_CUSTOMER_AMOUNT_RATIO, decimal(rules.extremeCustomerAmountRatio()), now);
        save(AppConfigRiskPolicyRepository.RULES_HIGH_BALANCE_RATIO, decimal(rules.highBalanceRatio()), now);
        save(AppConfigRiskPolicyRepository.RULES_HIGH_EXPECTED_TURNOVER_RATIO, decimal(rules.highExpectedTurnoverRatio()), now);
        return response();
    }

    private RiskPolicyConfigResponse response() {
        double customerWeight = doubleValue(AppConfigRiskPolicyRepository.CUSTOMER_WEIGHT);
        double peerWeight = doubleValue(AppConfigRiskPolicyRepository.PEER_WEIGHT);
        double mlEnsembleWeight = doubleValue(AppConfigRiskPolicyRepository.ML_ENSEMBLE_WEIGHT);
        Map<String, StoredModelAllocation> allocations = storedAllocations();
        Instant updatedAt = DEFINITIONS.keySet().stream()
                .map(appConfigRepository::findById)
                .flatMap(java.util.Optional::stream)
                .map(AppConfig::getUpdatedAt)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
        return new RiskPolicyConfigResponse(
                value(AppConfigRiskPolicyRepository.VERSION),
                learningMode(),
                customerWeight,
                peerWeight,
                mlEnsembleWeight,
                doubleValue(AppConfigRiskPolicyRepository.RULES_WEIGHT),
                new RiskPolicyConfigResponse.CustomerBehaviourSubWeights(
                        doubleValue(AppConfigRiskPolicyRepository.CB_AMOUNT_WEIGHT),
                        doubleValue(AppConfigRiskPolicyRepository.CB_NOVELTY_WEIGHT),
                        doubleValue(AppConfigRiskPolicyRepository.CB_FREQUENCY_WEIGHT),
                        doubleValue(AppConfigRiskPolicyRepository.CB_TIME_GAP_WEIGHT),
                        doubleValue(AppConfigRiskPolicyRepository.CB_UNUSUAL_HOUR_WEIGHT)
                ),
                new RiskPolicyConfigResponse.PeerBehaviourSubWeights(
                        doubleValue(AppConfigRiskPolicyRepository.PB_AMOUNT_WEIGHT),
                        doubleValue(AppConfigRiskPolicyRepository.PB_FREQUENCY_WEIGHT),
                        doubleValue(AppConfigRiskPolicyRepository.PB_EXPECTED_TURNOVER_WEIGHT)
                ),
                new RiskPolicyConfigResponse.AmlRuleThresholds(
                        doubleValue(AppConfigRiskPolicyRepository.RULES_REPORTING_THRESHOLD),
                        intValue(AppConfigRiskPolicyRepository.RULES_STRUCTURING_COUNT_24H),
                        intValue(AppConfigRiskPolicyRepository.RULES_RAPID_TX_COUNT_10M),
                        intValue(AppConfigRiskPolicyRepository.RULES_HIGH_TX_COUNT_1H),
                        intValue(AppConfigRiskPolicyRepository.RULES_MULTI_BENEFICIARY_COUNT_1H),
                        intValue(AppConfigRiskPolicyRepository.RULES_REPEATED_AMOUNT_COUNT_24H),
                        doubleValue(AppConfigRiskPolicyRepository.RULES_HIGH_CUSTOMER_AMOUNT_RATIO),
                        doubleValue(AppConfigRiskPolicyRepository.RULES_EXTREME_CUSTOMER_AMOUNT_RATIO),
                        doubleValue(AppConfigRiskPolicyRepository.RULES_HIGH_BALANCE_RATIO),
                        doubleValue(AppConfigRiskPolicyRepository.RULES_HIGH_EXPECTED_TURNOVER_RATIO)
                ),
                responseModels(allocations, mlEnsembleWeight),
                doubleValue(AppConfigRiskPolicyRepository.LOW_THRESHOLD),
                doubleValue(AppConfigRiskPolicyRepository.MEDIUM_THRESHOLD),
                doubleValue(AppConfigRiskPolicyRepository.HIGH_THRESHOLD),
                updatedAt
        );
    }

    private void save(String key, String value, Instant now) {
        Definition definition = DEFINITIONS.get(key);
        AppConfig config = appConfigRepository.findById(key).orElseGet(AppConfig::new);
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setValueType(definition.type());
        config.setDescription(definition.description());
        config.setUpdatedAt(now);
        appConfigRepository.save(config);
    }

    private String value(String key) {
        return appConfigRepository.findById(key)
                .map(AppConfig::getConfigValue)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElse(DEFINITIONS.get(key).defaultValue());
    }

    private double doubleValue(String key) {
        try {
            double value = Double.parseDouble(value(key));
            if (!Double.isFinite(value)) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            return Double.parseDouble(DEFINITIONS.get(key).defaultValue());
        }
    }

    private int intValue(String key) {
        try {
            return Integer.parseInt(value(key).strip());
        } catch (NumberFormatException e) {
            return Integer.parseInt(DEFINITIONS.get(key).defaultValue().strip());
        }
    }

    private static String decimal(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static Map<String, Definition> definitions() {
        Map<String, Definition> definitions = new LinkedHashMap<>();
        definitions.put(AppConfigRiskPolicyRepository.VERSION,
                new Definition("AML_RISK_POLICY_V3", "STRING", "Active layered risk policy version."));
        definitions.put(AppConfigRiskPolicyRepository.CUSTOMER_WEIGHT,
                new Definition("0.20", "DECIMAL", "Customer behaviour contribution to final risk."));
        definitions.put(AppConfigRiskPolicyRepository.PEER_WEIGHT,
                new Definition("0.15", "DECIMAL", "Peer behaviour contribution to final risk."));
        definitions.put(AppConfigRiskPolicyRepository.ML_ENSEMBLE_WEIGHT,
                new Definition("0.40", "DECIMAL", "ML model ensemble contribution to final risk."));
        definitions.put(AppConfigRiskPolicyRepository.RULES_WEIGHT,
                new Definition("0.25", "DECIMAL", "Deterministic AML rules contribution to final risk."));
        definitions.put(MODEL_ALLOCATIONS_JSON,
                new Definition("[]", "JSON", "Selected production ML models and their relative ensemble weights."));
        definitions.put(AppConfigRiskPolicyRepository.LOW_THRESHOLD,
                new Definition("0.40", "DECIMAL", "Inclusive LOW risk threshold."));
        definitions.put(AppConfigRiskPolicyRepository.MEDIUM_THRESHOLD,
                new Definition("0.65", "DECIMAL", "Inclusive MEDIUM risk and automatic case threshold."));
        definitions.put(AppConfigRiskPolicyRepository.HIGH_THRESHOLD,
                new Definition("0.80", "DECIMAL", "Inclusive HIGH risk threshold."));
        definitions.put(AppConfigRiskPolicyRepository.CB_AMOUNT_WEIGHT,
                new Definition("0.55", "DECIMAL", "Amount deviation sub-weight within customer behaviour scoring."));
        definitions.put(AppConfigRiskPolicyRepository.CB_NOVELTY_WEIGHT,
                new Definition("0.20", "DECIMAL", "Novelty (new beneficiary/location/channel/device) sub-weight within customer behaviour scoring."));
        definitions.put(AppConfigRiskPolicyRepository.CB_FREQUENCY_WEIGHT,
                new Definition("0.12", "DECIMAL", "Velocity burst sub-weight within customer behaviour scoring."));
        definitions.put(AppConfigRiskPolicyRepository.CB_TIME_GAP_WEIGHT,
                new Definition("0.08", "DECIMAL", "Rapid time-gap sub-weight within customer behaviour scoring."));
        definitions.put(AppConfigRiskPolicyRepository.CB_UNUSUAL_HOUR_WEIGHT,
                new Definition("0.05", "DECIMAL", "Unusual transaction hour sub-weight within customer behaviour scoring."));
        definitions.put(AppConfigRiskPolicyRepository.PB_AMOUNT_WEIGHT,
                new Definition("0.60", "DECIMAL", "Amount deviation sub-weight within peer behaviour scoring."));
        definitions.put(AppConfigRiskPolicyRepository.PB_FREQUENCY_WEIGHT,
                new Definition("0.25", "DECIMAL", "Frequency percentile sub-weight within peer behaviour scoring."));
        definitions.put(AppConfigRiskPolicyRepository.PB_EXPECTED_TURNOVER_WEIGHT,
                new Definition("0.15", "DECIMAL", "Expected turnover sub-weight within peer behaviour scoring."));
        definitions.put(AppConfigRiskPolicyRepository.RULES_REPORTING_THRESHOLD,
                new Definition("10000.0", "DECIMAL", "Regulatory reporting threshold used in structuring detection."));
        definitions.put(AppConfigRiskPolicyRepository.RULES_STRUCTURING_COUNT_24H,
                new Definition("3", "INTEGER", "Minimum below-threshold transaction count in 24 h to flag structuring."));
        definitions.put(AppConfigRiskPolicyRepository.RULES_RAPID_TX_COUNT_10M,
                new Definition("5", "INTEGER", "Transaction count in 10 minutes that triggers rapid velocity rule."));
        definitions.put(AppConfigRiskPolicyRepository.RULES_HIGH_TX_COUNT_1H,
                new Definition("10", "INTEGER", "Transaction count in 1 hour that triggers high velocity rule."));
        definitions.put(AppConfigRiskPolicyRepository.RULES_MULTI_BENEFICIARY_COUNT_1H,
                new Definition("4", "INTEGER", "Unique beneficiary count in 1 hour that triggers the multiple-beneficiary rule."));
        definitions.put(AppConfigRiskPolicyRepository.RULES_REPEATED_AMOUNT_COUNT_24H,
                new Definition("4", "INTEGER", "Identical-amount transaction count in 24 h that triggers the repeated-amount rule."));
        definitions.put(AppConfigRiskPolicyRepository.RULES_HIGH_CUSTOMER_AMOUNT_RATIO,
                new Definition("4.0", "DECIMAL", "Multiplier vs 30-day customer average that triggers the high-amount rule."));
        definitions.put(AppConfigRiskPolicyRepository.RULES_EXTREME_CUSTOMER_AMOUNT_RATIO,
                new Definition("8.0", "DECIMAL", "Multiplier vs 30-day customer average that triggers the extreme-amount rule."));
        definitions.put(AppConfigRiskPolicyRepository.RULES_HIGH_BALANCE_RATIO,
                new Definition("0.80", "DECIMAL", "Transaction-to-balance ratio threshold that triggers the high-balance rule."));
        definitions.put(AppConfigRiskPolicyRepository.RULES_HIGH_EXPECTED_TURNOVER_RATIO,
                new Definition("0.50", "DECIMAL", "Transaction-to-expected-turnover ratio threshold that triggers the turnover rule."));
        return Map.copyOf(definitions);
    }

    private record Definition(String defaultValue, String type, String description) {
    }

    private Map<String, StoredModelAllocation> validatedModels(List<RiskPolicyModelConfigRequest> requestedModels) {
        Map<String, SupportedModel> supportedModels = activeModels();
        List<RiskPolicyModelConfigRequest> models = requestedModels == null ? List.of() : requestedModels;
        Map<String, RiskPolicyModelConfigRequest> byKey = models.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        model -> safeKey(model.modelKey()),
                        model -> model,
                        (_first, second) -> second,
                        LinkedHashMap::new
                ));
        if (byKey.isEmpty()) {
            throw new IllegalArgumentException("At least one ML model configuration is required");
        }
        List<StoredModelAllocation> allocations = new ArrayList<>();
        double enabledTotal = 0.0;
        List<String> unsupported = byKey.keySet().stream()
                .filter(key -> !supportedModels.containsKey(key))
                .toList();
        if (!unsupported.isEmpty()) {
            throw new IllegalArgumentException("Models do not match the active " + learningMode()
                    + " system type: " + String.join(", ", unsupported));
        }
        for (SupportedModel model : supportedModels.values()) {
            RiskPolicyModelConfigRequest request = byKey.get(model.modelKey());
            boolean enabled = request != null && request.enabled();
            double weight = enabled ? request.weight() : 0.0;
            if (!Double.isFinite(weight) || weight < 0.0 || weight > 1.0) {
                throw new IllegalArgumentException("ML model weights must stay between 0.0 and 1.0");
            }
            if (enabled) enabledTotal += weight;
            allocations.add(new StoredModelAllocation(model.modelKey(), enabled, weight));
        }
        if (allocations.stream().noneMatch(StoredModelAllocation::enabled)) {
            throw new IllegalArgumentException("Select at least one ML model for production scoring");
        }
        rejectStackedAndBaseModelCombination(allocations);
        if (Math.abs(enabledTotal - 1.0) > 0.000001) {
            throw new IllegalArgumentException("Enabled ML model weights must total exactly 1.0");
        }
        return allocations.stream().collect(Collectors.toMap(
                StoredModelAllocation::modelKey,
                allocation -> allocation,
                (_first, second) -> second,
                LinkedHashMap::new
        ));
    }

    private List<RiskPolicyModelConfigResponse> responseModels(
            Map<String, StoredModelAllocation> allocations,
            double mlEnsembleWeight
    ) {
        return activeModels().values().stream()
                .map(model -> {
                    StoredModelAllocation allocation = allocations.getOrDefault(
                            model.modelKey(), new StoredModelAllocation(model.modelKey(), false, 0.0)
                    );
                    return new RiskPolicyModelConfigResponse(
                            model.modelKey(),
                            model.displayName(),
                            model.family(),
                            allocation.enabled(),
                            allocation.enabled() ? allocation.weight() : 0.0,
                            allocation.enabled() ? allocation.weight() * mlEnsembleWeight : 0.0,
                            model.productionReady()
                    );
                })
                .sorted(Comparator.comparing(RiskPolicyModelConfigResponse::family)
                        .thenComparing(RiskPolicyModelConfigResponse::displayName))
                .toList();
    }

    private Map<String, StoredModelAllocation> storedAllocations() {
        Map<String, SupportedModel> supportedModels = activeModels();
        String raw = value(MODEL_ALLOCATIONS_JSON);
        if (!raw.isBlank() && !"[]".equals(raw)) {
            try {
                Map<String, StoredModelAllocation> parsed = objectMapper.readValue(raw, MODEL_LIST).stream()
                        .filter(Objects::nonNull)
                        .filter(model -> supportedModels.containsKey(model.modelKey()))
                        .collect(Collectors.toMap(
                                StoredModelAllocation::modelKey,
                                allocation -> allocation,
                                (_first, second) -> second,
                                LinkedHashMap::new
                        ));
                if (!parsed.isEmpty()) {
                    return normalizedAllocations(parsed);
                }
            } catch (Exception ignored) {
            }
        }
        return defaultAllocation();
    }

    private Map<String, StoredModelAllocation> normalizedAllocations(Map<String, StoredModelAllocation> allocations) {
        allocations = withoutStackedModelWhenBaseModelsAreEnabled(allocations);
        double total = allocations.values().stream()
                .filter(StoredModelAllocation::enabled)
                .mapToDouble(StoredModelAllocation::weight)
                .filter(weight -> Double.isFinite(weight) && weight > 0.0)
                .sum();
        if (total <= 0.0) {
            return defaultAllocation();
        }
        return allocations.values().stream().collect(Collectors.toMap(
                StoredModelAllocation::modelKey,
                allocation -> new StoredModelAllocation(
                        allocation.modelKey(), allocation.enabled(),
                        allocation.enabled() ? allocation.weight() / total : 0.0
                ),
                (_first, second) -> second,
                LinkedHashMap::new
        ));
    }

    private void rejectStackedAndBaseModelCombination(List<StoredModelAllocation> allocations) {
        boolean stackedEnabled = allocations.stream()
                .anyMatch(allocation -> allocation.enabled() && "STACKED_ENSEMBLE".equals(allocation.modelKey()));
        boolean baseEnabled = allocations.stream()
                .anyMatch(allocation -> allocation.enabled() && isSupervisedBaseModel(allocation.modelKey()));
        if (stackedEnabled && baseEnabled) {
            throw new IllegalArgumentException(
                    "Temporal Stacked Ensemble already combines the three base classifiers. "
                            + "Select it alone, or choose weighted base classifiers."
            );
        }
    }

    private Map<String, StoredModelAllocation> withoutStackedModelWhenBaseModelsAreEnabled(
            Map<String, StoredModelAllocation> allocations
    ) {
        boolean baseEnabled = allocations.values().stream()
                .anyMatch(allocation -> allocation.enabled() && isSupervisedBaseModel(allocation.modelKey()));
        if (!baseEnabled) return allocations;
        return allocations.values().stream()
                .filter(allocation -> !"STACKED_ENSEMBLE".equals(allocation.modelKey()))
                .collect(Collectors.toMap(
                        StoredModelAllocation::modelKey,
                        allocation -> allocation,
                        (_first, second) -> second,
                        LinkedHashMap::new
                ));
    }

    private boolean isSupervisedBaseModel(String modelKey) {
        return Set.of("XGBOOST_CLASSIFIER", "RANDOM_FOREST_CLASSIFIER", "EXTRA_TREES_CLASSIFIER").contains(modelKey);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to save risk policy model configuration", exception);
        }
    }

    private String safeKey(String key) {
        return key == null ? "" : key.trim().toUpperCase();
    }

    private String learningMode() {
        return appConfigRepository.findById(SYSTEM_LEARNING_MODE)
                .map(AppConfig::getConfigValue)
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(value -> value.equals("SUPERVISED") || value.equals("UNSUPERVISED"))
                .orElse("UNSUPERVISED");
    }

    private Map<String, SupportedModel> activeModels() {
        return "SUPERVISED".equals(learningMode()) ? SUPERVISED_MODELS : UNSUPERVISED_MODELS;
    }

    private Map<String, StoredModelAllocation> defaultAllocation() {
        String modelKey = activeModels().keySet().iterator().next();
        return Map.of(modelKey, new StoredModelAllocation(modelKey, true, 1.0));
    }

    private static Map<String, SupportedModel> unsupervisedModels() {
        Map<String, SupportedModel> models = new LinkedHashMap<>();
        models.put("ISOLATION_FOREST", new SupportedModel("ISOLATION_FOREST", "Isolation Forest", "UNSUPERVISED", true));
        models.put("AUTOENCODER", new SupportedModel("AUTOENCODER", "Autoencoder", "UNSUPERVISED", true));
        models.put("BEHAVIORAL_CLUSTER_OUTLIER", new SupportedModel("BEHAVIORAL_CLUSTER_OUTLIER", "Behavioral Cluster Outlier", "UNSUPERVISED", true));
        return Map.copyOf(models);
    }

    private static Map<String, SupportedModel> supervisedModels() {
        Map<String, SupportedModel> models = new LinkedHashMap<>();
        models.put("XGBOOST_CLASSIFIER", new SupportedModel("XGBOOST_CLASSIFIER", "XGBoost", "SUPERVISED", true));
        models.put("RANDOM_FOREST_CLASSIFIER", new SupportedModel("RANDOM_FOREST_CLASSIFIER", "Class-Balanced Random Forest", "SUPERVISED", true));
        models.put("EXTRA_TREES_CLASSIFIER", new SupportedModel("EXTRA_TREES_CLASSIFIER", "Extra Trees", "SUPERVISED", true));
        models.put("STACKED_ENSEMBLE", new SupportedModel("STACKED_ENSEMBLE", "Temporal Stacked Ensemble", "SUPERVISED", true));
        return Map.copyOf(models);
    }

    private record StoredModelAllocation(String modelKey, boolean enabled, double weight) {
    }

    private record SupportedModel(String modelKey, String displayName, String family, boolean productionReady) {
    }
}
