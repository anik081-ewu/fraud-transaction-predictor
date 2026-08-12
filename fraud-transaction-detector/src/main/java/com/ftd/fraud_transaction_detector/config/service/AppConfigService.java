package com.ftd.fraud_transaction_detector.config.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.config.repo.AppConfigRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AppConfigService {

    public static final String MIN_TXN_COUNT_BEFORE_PREDICT = "ml.min_transaction_count_before_predict";
    public static final String ISO_N_ESTIMATORS = "ml.iso.n_estimators";
    public static final String ISO_MAX_SAMPLES = "ml.iso.max_samples";
    public static final String ISO_CONTAMINATION = "ml.iso.contamination";
    public static final String LOF_N_NEIGHBORS = "ml.lof.n_neighbors";
    public static final String LOF_CONTAMINATION = "ml.lof.contamination";
    public static final String SVM_KERNEL = "ml.svm.kernel";
    public static final String SVM_GAMMA = "ml.svm.gamma";
    public static final String SVM_NU = "ml.svm.nu";
    public static final String ELLIPTIC_CONTAMINATION = "ml.elliptic.contamination";
    public static final String ELLIPTIC_SUPPORT_FRACTION = "ml.elliptic.support_fraction";
    public static final String PCA_N_COMPONENTS = "ml.pca.n_components";
    public static final String PCA_RECONSTRUCTION_PERCENTILE = "ml.pca.reconstruction_percentile";
    public static final String ML_RANDOM_STATE = "ml.random_state";
    public static final String GATING_ENABLED = "ml.gating.enabled";
    public static final String GATING_LOF_DEC_MEDIUM = "ml.gating.lof_decision_medium";
    public static final String GATING_SVM_DEC_MEDIUM = "ml.gating.svm_decision_medium";
    public static final String OPTIMIZATION_ENABLED = "ml.optimization.enabled";
    public static final String OPTIMIZATION_VALIDATION_FRACTION = "ml.optimization.validation_fraction";
    public static final String OPTIMIZATION_TARGET_ANOMALY_RATE = "ml.optimization.target_anomaly_rate";
    public static final String OPTIMIZATION_MIN_ROWS = "ml.optimization.min_rows";
    public static final String OPTIMIZATION_MAX_TRAINING_ROWS = "ml.optimization.max_training_rows";
    public static final String AML_STRUCTURING_REPORTING_THRESHOLD = "aml.structuring.reporting_threshold";
    public static final String AML_EXPORT_BASE_PATH = "aml.export.base_path";
    public static final String AML_EXPORT_CHUNK_SIZE = "aml.export.chunk_size";
    public static final String AML_EXPORT_ROWS_PER_FILE = "aml.export.rows_per_file";
    public static final String AML_MODEL_ARTIFACT_BASE_PATH = "aml.model.artifact_base_path";
    public static final String AML_HST_ENABLED = "aml.hst.enabled";
    public static final String AML_HST_N_TREES = "aml.hst.n_trees";
    public static final String AML_HST_HEIGHT = "aml.hst.height";
    public static final String AML_HST_WINDOW_SIZE = "aml.hst.window_size";
    public static final String AML_HST_THRESHOLD_QUANTILE = "aml.hst.threshold_quantile";
    public static final String AML_HST_PARQUET_BATCH_SIZE = "aml.hst.parquet_batch_size";
    public static final String AML_HST_SEED = "aml.hst.seed";
    public static final String AML_ONLINE_OCSVM_ENABLED = "aml.online_ocsvm.enabled";
    public static final String AML_ONLINE_OCSVM_NU = "aml.online_ocsvm.nu";
    public static final String AML_ONLINE_OCSVM_LEARNING_RATE = "aml.online_ocsvm.learning_rate";
    public static final String AML_ONLINE_OCSVM_INTERCEPT_LEARNING_RATE = "aml.online_ocsvm.intercept_learning_rate";
    public static final String AML_ONLINE_OCSVM_GAMMA = "aml.online_ocsvm.gamma";
    public static final String AML_ONLINE_OCSVM_N_COMPONENTS = "aml.online_ocsvm.n_components";
    public static final String AML_ONLINE_OCSVM_THRESHOLD_QUANTILE = "aml.online_ocsvm.threshold_quantile";
    public static final String AML_ONLINE_OCSVM_MIN_CALIBRATION_ROWS = "aml.online_ocsvm.min_calibration_rows";
    public static final String AML_ONLINE_OCSVM_PARQUET_BATCH_SIZE = "aml.online_ocsvm.parquet_batch_size";
    public static final String AML_ONLINE_OCSVM_SEED = "aml.online_ocsvm.seed";
    public static final String AML_VALIDATION_MIN_ROWS = "aml.validation.min_rows";
    public static final String AML_VALIDATION_MIN_ANOMALY_RATE = "aml.validation.min_anomaly_rate";
    public static final String AML_VALIDATION_MAX_ANOMALY_RATE = "aml.validation.max_anomaly_rate";
    public static final String AML_VALIDATION_MAX_DAILY_RATE_STDDEV = "aml.validation.max_daily_rate_stddev";
    public static final String AML_VALIDATION_MIN_REVIEWED_ALERTS = "aml.validation.min_reviewed_alerts";
    public static final String AML_VALIDATION_MIN_REVIEWED_PRECISION = "aml.validation.min_reviewed_precision";
    public static final String AML_LAYERED_VALIDATION_MIN_ROWS = "aml.layered_validation.min_rows";
    public static final String AML_LAYERED_VALIDATION_MIN_OBSERVATION_DAYS = "aml.layered_validation.min_observation_days";
    public static final String AML_LAYERED_VALIDATION_MIN_LEGACY_ALERTS = "aml.layered_validation.min_legacy_alerts";
    public static final String AML_LAYERED_VALIDATION_MAX_ALERT_RATE = "aml.layered_validation.max_alert_rate";
    public static final String AML_LAYERED_VALIDATION_MAX_ALERT_VOLUME_INCREASE = "aml.layered_validation.max_alert_volume_increase";
    public static final String AML_LAYERED_VALIDATION_MIN_TOP_RISK_OVERLAP = "aml.layered_validation.min_top_risk_overlap";
    public static final String AML_LAYERED_VALIDATION_MAX_DAILY_RATE_STDDEV = "aml.layered_validation.max_daily_rate_stddev";
    public static final String AML_LAYERED_VALIDATION_MAX_SEGMENT_DAILY_STDDEV = "aml.layered_validation.max_segment_daily_stddev";
    public static final String AML_LAYERED_VALIDATION_MIN_SYNTHETIC_SCENARIOS = "aml.layered_validation.min_synthetic_scenarios";
    public static final String AML_LAYERED_VALIDATION_MIN_SYNTHETIC_RECALL = "aml.layered_validation.min_synthetic_recall";
    public static final String AML_LAYERED_VALIDATION_MIN_REVIEWED_ALERTS = "aml.layered_validation.min_reviewed_alerts";
    public static final String AML_LAYERED_VALIDATION_MAX_REVIEWED_FALSE_POSITIVE_RATE = "aml.layered_validation.max_reviewed_false_positive_rate";
    public static final String AML_LAYERED_VALIDATION_MAX_P95_LATENCY_MS = "aml.layered_validation.max_p95_latency_ms";
    public static final String AML_LAYERED_VALIDATION_MIN_MODEL_AVAILABILITY = "aml.layered_validation.min_model_availability";
    public static final String AML_LAYERED_VALIDATION_MAX_AVERAGE_INCREMENTAL_UPDATE_MS = "aml.layered_validation.max_average_incremental_update_ms";
    public static final String AML_LAYERED_DEPLOYMENT_MAX_VALIDATION_AGE_DAYS = "aml.layered_deployment.max_validation_age_days";
    public static final String AML_RISK_MODEL_ALLOCATIONS_JSON = "aml.risk.ml_model_allocations_json";
    public static final String AML_RESEARCH_MINIMUM_ROWS = "aml.research.minimum_rows";
    public static final String AML_RESEARCH_HOLDOUT_FRACTION = "aml.research.holdout_fraction";
    public static final String AML_RESEARCH_MAXIMUM_EVALUATION_ROWS = "aml.research.maximum_evaluation_rows";
    public static final String AML_RESEARCH_RANDOM_SEED = "aml.research.random_seed";
    public static final String AML_RESEARCH_ISOLATION_FOREST_N_ESTIMATORS = "aml.research.isolation_forest_n_estimators";
    public static final String AML_RESEARCH_ISOLATION_FOREST_MAX_TRAINING_ROWS = "aml.research.isolation_forest_max_training_rows";
    public static final String AML_RESEARCH_AUTOENCODER_MAX_TRAINING_ROWS = "aml.research.autoencoder_max_training_rows";
    private static final TypeReference<List<ProductionModelAllocation>> MODEL_ALLOCATIONS = new TypeReference<>() {};
    private static final Set<String> BATCH_MODEL_KEYS = Set.of(
            "ISOLATION_FOREST",
            "ONE_CLASS_SVM",
            "AUTOENCODER"
    );

    private final AppConfigRepository appConfigRepository;
    private final ObjectMapper objectMapper;

    public AppConfigService(AppConfigRepository appConfigRepository, ObjectMapper objectMapper) {
        this.appConfigRepository = appConfigRepository;
        this.objectMapper = objectMapper;
    }

    public boolean isColdStartEnabled(boolean defaultValue) {
        return appConfigRepository.findById("ml.cold_start.enabled")
                .map(c -> Boolean.parseBoolean(c.getConfigValue()))
                .orElse(defaultValue);
    }

    public int getMinTransactionCountBeforePredict(int defaultValue) {
        return appConfigRepository.findById(MIN_TXN_COUNT_BEFORE_PREDICT)
                .map(c -> parseIntSafe(c.getConfigValue(), defaultValue))
                .orElse(defaultValue);
    }

    public BigDecimal getStructuringReportingThreshold(BigDecimal defaultValue) {
        return appConfigRepository.findById(AML_STRUCTURING_REPORTING_THRESHOLD)
                .map(config -> parseDecimalSafe(config.getConfigValue(), defaultValue))
                .orElse(defaultValue);
    }

    public String getExportBasePath(String defaultValue) {
        return getString(AML_EXPORT_BASE_PATH, defaultValue);
    }

    public int getExportChunkSize(int defaultValue) {
        return positiveInt(AML_EXPORT_CHUNK_SIZE, defaultValue);
    }

    public int getExportRowsPerFile(int defaultValue) {
        return positiveInt(AML_EXPORT_ROWS_PER_FILE, defaultValue);
    }

    public String getModelArtifactBasePath(String defaultValue) {
        return getString(AML_MODEL_ARTIFACT_BASE_PATH, defaultValue);
    }

    public boolean isHstEnabled(boolean defaultValue) {
        return appConfigRepository.findById(AML_HST_ENABLED)
                .map(config -> Boolean.parseBoolean(config.getConfigValue().trim()))
                .orElse(defaultValue);
    }

    /**
     * Config key behind each model's "Training enabled" switch on the Model Tuning page.
     *
     * The three batch keys previously had no reader at all, so switching those models off
     * changed nothing — the pipeline trained them regardless.
     */
    private static final Map<String, String> MODEL_TRAINING_ENABLED_KEYS = Map.of(
            "ISOLATION_FOREST", "aml.isolation_forest.enabled",
            "ONE_CLASS_SVM", "aml.ocsvm_batch.enabled",
            "AUTOENCODER", "aml.autoencoder.enabled",
            "HALF_SPACE_TREES", AML_HST_ENABLED,
            "ONLINE_ONE_CLASS_SVM", AML_ONLINE_OCSVM_ENABLED
    );

    /** Whether a model type should produce new candidates during a training run. */
    public boolean isModelTrainingEnabled(String modelType, boolean defaultValue) {
        String key = MODEL_TRAINING_ENABLED_KEYS.get(modelType);
        if (key == null) return defaultValue;
        return appConfigRepository.findById(key)
                .map(config -> Boolean.parseBoolean(config.getConfigValue().trim()))
                .orElse(defaultValue);
    }

    public Map<String, Object> getHstParameters() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("nTrees", positiveInt(AML_HST_N_TREES, 25));
        parameters.put("height", positiveInt(AML_HST_HEIGHT, 8));
        parameters.put("windowSize", positiveInt(AML_HST_WINDOW_SIZE, 250));
        parameters.put("thresholdQuantile", doubleValue(AML_HST_THRESHOLD_QUANTILE, 0.99));
        parameters.put("batchSize", positiveInt(AML_HST_PARQUET_BATCH_SIZE, 65_536));
        parameters.put("seed", positiveInt(AML_HST_SEED, 42));
        return parameters;
    }

    public boolean isOnlineOneClassSvmEnabled(boolean defaultValue) {
        return appConfigRepository.findById(AML_ONLINE_OCSVM_ENABLED)
                .map(config -> Boolean.parseBoolean(config.getConfigValue().trim()))
                .orElse(defaultValue);
    }

    public Map<String, Object> getOnlineOneClassSvmParameters() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("nu", doubleValue(AML_ONLINE_OCSVM_NU, 0.05));
        parameters.put("learningRate", doubleValue(AML_ONLINE_OCSVM_LEARNING_RATE, 0.01));
        parameters.put("interceptLearningRate", doubleValue(AML_ONLINE_OCSVM_INTERCEPT_LEARNING_RATE, 0.01));
        parameters.put("gamma", doubleValue(AML_ONLINE_OCSVM_GAMMA, 0.5));
        parameters.put("nComponents", positiveInt(AML_ONLINE_OCSVM_N_COMPONENTS, 64));
        parameters.put("thresholdQuantile", doubleValue(AML_ONLINE_OCSVM_THRESHOLD_QUANTILE, 0.99));
        parameters.put("minimumCalibrationRows", positiveInt(AML_ONLINE_OCSVM_MIN_CALIBRATION_ROWS, 200));
        parameters.put("batchSize", positiveInt(AML_ONLINE_OCSVM_PARQUET_BATCH_SIZE, 65_536));
        parameters.put("seed", positiveInt(AML_ONLINE_OCSVM_SEED, 42));
        return parameters;
    }

    public boolean isLayeredShadowEnabled(boolean defaultValue) {
        return booleanValue("aml.risk.layered_shadow_enabled", defaultValue);
    }

    public boolean isLegacyComparisonEnabled(boolean defaultValue) {
        return booleanValue("aml.risk.legacy_comparison_enabled", defaultValue);
    }

    public int getValidationMinRows() {
        return positiveInt(AML_VALIDATION_MIN_ROWS, 1_000);
    }

    public double getValidationMinAnomalyRate() {
        return doubleValue(AML_VALIDATION_MIN_ANOMALY_RATE, 0.001);
    }

    public double getValidationMaxAnomalyRate() {
        return doubleValue(AML_VALIDATION_MAX_ANOMALY_RATE, 0.10);
    }

    public double getValidationMaxDailyRateStddev() {
        return doubleValue(AML_VALIDATION_MAX_DAILY_RATE_STDDEV, 0.05);
    }

    public int getValidationMinReviewedAlerts() {
        return positiveInt(AML_VALIDATION_MIN_REVIEWED_ALERTS, 20);
    }

    public double getValidationMinReviewedPrecision() {
        return doubleValue(AML_VALIDATION_MIN_REVIEWED_PRECISION, 0.20);
    }

    public int getLayeredValidationMinRows() {
        return requiredPositiveInt(AML_LAYERED_VALIDATION_MIN_ROWS);
    }

    public int getLayeredValidationMinObservationDays() {
        return requiredPositiveInt(AML_LAYERED_VALIDATION_MIN_OBSERVATION_DAYS);
    }

    public int getLayeredValidationMinLegacyAlerts() {
        return requiredPositiveInt(AML_LAYERED_VALIDATION_MIN_LEGACY_ALERTS);
    }

    public double getLayeredValidationMaxAlertRate() {
        return requiredDouble(AML_LAYERED_VALIDATION_MAX_ALERT_RATE);
    }

    public double getLayeredValidationMaxAlertVolumeIncrease() {
        return requiredDouble(AML_LAYERED_VALIDATION_MAX_ALERT_VOLUME_INCREASE);
    }

    public double getLayeredValidationMinTopRiskOverlap() {
        return requiredDouble(AML_LAYERED_VALIDATION_MIN_TOP_RISK_OVERLAP);
    }

    public double getLayeredValidationMaxDailyRateStddev() {
        return requiredDouble(AML_LAYERED_VALIDATION_MAX_DAILY_RATE_STDDEV);
    }

    public double getLayeredValidationMaxSegmentDailyStddev() {
        return requiredDouble(AML_LAYERED_VALIDATION_MAX_SEGMENT_DAILY_STDDEV);
    }

    public int getLayeredValidationMinSyntheticScenarios() {
        return requiredPositiveInt(AML_LAYERED_VALIDATION_MIN_SYNTHETIC_SCENARIOS);
    }

    public double getLayeredValidationMinSyntheticRecall() {
        return requiredDouble(AML_LAYERED_VALIDATION_MIN_SYNTHETIC_RECALL);
    }

    public int getLayeredValidationMinReviewedAlerts() {
        return requiredPositiveInt(AML_LAYERED_VALIDATION_MIN_REVIEWED_ALERTS);
    }

    public double getLayeredValidationMaxReviewedFalsePositiveRate() {
        return requiredDouble(AML_LAYERED_VALIDATION_MAX_REVIEWED_FALSE_POSITIVE_RATE);
    }

    public double getLayeredValidationMaxP95LatencyMs() {
        return requiredDouble(AML_LAYERED_VALIDATION_MAX_P95_LATENCY_MS);
    }

    public double getLayeredValidationMinModelAvailability() {
        return requiredDouble(AML_LAYERED_VALIDATION_MIN_MODEL_AVAILABILITY);
    }

    public double getLayeredValidationMaxAverageIncrementalUpdateMs() {
        return requiredDouble(AML_LAYERED_VALIDATION_MAX_AVERAGE_INCREMENTAL_UPDATE_MS);
    }

    public int getLayeredDeploymentMaxValidationAgeDays() {
        return requiredPositiveInt(AML_LAYERED_DEPLOYMENT_MAX_VALIDATION_AGE_DAYS);
    }

    public int getResearchMinimumRows() {
        return positiveInt(AML_RESEARCH_MINIMUM_ROWS, 200);
    }

    public double getResearchHoldoutFraction() {
        return doubleValue(AML_RESEARCH_HOLDOUT_FRACTION, 0.20);
    }

    public int getResearchMaximumEvaluationRows() {
        return positiveInt(AML_RESEARCH_MAXIMUM_EVALUATION_ROWS, 20_000);
    }

    public int getResearchRandomSeed() {
        return positiveInt(AML_RESEARCH_RANDOM_SEED, 42);
    }

    public int getResearchIsolationForestEstimators() {
        return positiveInt(AML_RESEARCH_ISOLATION_FOREST_N_ESTIMATORS, 200);
    }

    public int getResearchIsolationForestMaximumTrainingRows() {
        return positiveInt(AML_RESEARCH_ISOLATION_FOREST_MAX_TRAINING_ROWS, 100_000);
    }

    public int getResearchAutoencoderMaxTrainingRows() {
        return positiveInt(AML_RESEARCH_AUTOENCODER_MAX_TRAINING_ROWS, 50_000);
    }

    public Map<String, Double> getEnabledRiskPolicyModelWeights() {
        String raw = getString(AML_RISK_MODEL_ALLOCATIONS_JSON, "[]");
        if (!raw.isBlank() && !"[]".equals(raw)) {
            try {
                Map<String, Double> parsed = objectMapper.readValue(raw, MODEL_ALLOCATIONS).stream()
                        .filter(Objects::nonNull)
                        .filter(ProductionModelAllocation::enabled)
                        .filter(allocation -> allocation.modelKey() != null && !allocation.modelKey().isBlank())
                        .filter(allocation -> Double.isFinite(allocation.weight()) && allocation.weight() > 0.0)
                        .collect(Collectors.toMap(
                                allocation -> allocation.modelKey().trim().toUpperCase(),
                                ProductionModelAllocation::weight,
                                (_first, second) -> second,
                                LinkedHashMap::new
                        ));
                if (!parsed.isEmpty()) {
                    return Map.copyOf(parsed);
                }
            } catch (Exception ignored) {
            }
        }
        Map<String, Double> legacy = new LinkedHashMap<>();
        putIfPositive(legacy, "ISOLATION_FOREST", doubleValue("aml.risk.weight.isolation_forest", 0.0));
        putIfPositive(legacy, "HALF_SPACE_TREES", doubleValue("aml.risk.weight.half_space_trees", 0.0));
        putIfPositive(legacy, "ONLINE_ONE_CLASS_SVM", doubleValue("aml.risk.weight.online_ocsvm", 0.0));
        if (legacy.isEmpty()) {
            legacy.put("ISOLATION_FOREST", 1.0);
        }
        return normalizeWeights(legacy);
    }

    public Map<String, Double> getEnabledBatchRiskPolicyModelWeights() {
        return getEnabledRiskPolicyModelWeights().entrySet().stream()
                .filter(entry -> BATCH_MODEL_KEYS.contains(entry.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (_first, second) -> second,
                        LinkedHashMap::new
                ));
    }

    private String getString(String key, String defaultValue) {
        return appConfigRepository.findById(key)
                .map(config -> config.getConfigValue())
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElse(defaultValue);
    }

    private int positiveInt(String key, int defaultValue) {
        int parsed = appConfigRepository.findById(key)
                .map(config -> parseIntSafe(config.getConfigValue(), defaultValue))
                .orElse(defaultValue);
        return parsed > 0 ? parsed : defaultValue;
    }

    private double doubleValue(String key, double defaultValue) {
        return appConfigRepository.findById(key)
                .map(config -> {
                    try {
                        return Double.parseDouble(config.getConfigValue().trim());
                    } catch (RuntimeException exception) {
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    private boolean booleanValue(String key, boolean defaultValue) {
        return appConfigRepository.findById(key)
                .map(config -> config.getConfigValue())
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Boolean::parseBoolean)
                .orElse(defaultValue);
    }

    private int requiredPositiveInt(String key) {
        String raw = appConfigRepository.findById(key)
                .map(config -> config.getConfigValue())
                .orElseThrow(() -> new IllegalStateException("Missing required app_config key: " + key));
        try {
            int value = Integer.parseInt(raw.trim());
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid positive integer app_config value for " + key, exception);
        }
    }

    private double requiredDouble(String key) {
        String raw = appConfigRepository.findById(key)
                .map(config -> config.getConfigValue())
                .orElseThrow(() -> new IllegalStateException("Missing required app_config key: " + key));
        try {
            double value = Double.parseDouble(raw.trim());
            if (!Double.isFinite(value)) throw new NumberFormatException();
            return value;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid numeric app_config value for " + key, exception);
        }
    }

    private static BigDecimal parseDecimalSafe(String raw, BigDecimal defaultValue) {
        if (raw == null) return defaultValue;
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private static int parseIntSafe(String raw, int defaultValue) {
        if (raw == null) return defaultValue;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public Map<String, Object> getMlHyperparams() {
        Map<String, Object> hp = new LinkedHashMap<>();
        putIfPresent(hp, ISO_N_ESTIMATORS);
        putIfPresent(hp, ISO_MAX_SAMPLES);
        putIfPresent(hp, ISO_CONTAMINATION);
        putIfPresent(hp, LOF_N_NEIGHBORS);
        putIfPresent(hp, LOF_CONTAMINATION);
        putIfPresent(hp, SVM_KERNEL);
        putIfPresent(hp, SVM_GAMMA);
        putIfPresent(hp, SVM_NU);
        putIfPresent(hp, ELLIPTIC_CONTAMINATION);
        putIfPresent(hp, ELLIPTIC_SUPPORT_FRACTION);
        putIfPresent(hp, PCA_N_COMPONENTS);
        putIfPresent(hp, PCA_RECONSTRUCTION_PERCENTILE);
        putIfPresent(hp, ML_RANDOM_STATE);
        putIfPresent(hp, GATING_ENABLED);
        putIfPresent(hp, GATING_LOF_DEC_MEDIUM);
        putIfPresent(hp, GATING_SVM_DEC_MEDIUM);
        putIfPresent(hp, OPTIMIZATION_ENABLED);
        putIfPresent(hp, OPTIMIZATION_VALIDATION_FRACTION);
        putIfPresent(hp, OPTIMIZATION_TARGET_ANOMALY_RATE);
        putIfPresent(hp, OPTIMIZATION_MIN_ROWS);
        putIfPresent(hp, OPTIMIZATION_MAX_TRAINING_ROWS);
        return hp;
    }

    private void putIfPresent(Map<String, Object> target, String key) {
        appConfigRepository.findById(key)
                .map(c -> c.getConfigValue())
                .map(v -> v == null ? null : v.trim())
                .filter(v -> v != null && !v.isBlank())
                .ifPresent(v -> target.put(key, v));
    }

    private void putIfPositive(Map<String, Double> target, String key, double value) {
        if (Double.isFinite(value) && value > 0.0) {
            target.put(key, value);
        }
    }

    private Map<String, Double> normalizeWeights(Map<String, Double> weights) {
        double total = weights.values().stream()
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .filter(Double::isFinite)
                .filter(value -> value > 0.0)
                .sum();
        if (total <= 0.0) {
            return Map.of();
        }
        LinkedHashMap<String, Double> normalized = new LinkedHashMap<>();
        weights.forEach((key, value) -> {
            if (value != null && Double.isFinite(value) && value > 0.0) {
                normalized.put(key, value / total);
            }
        });
        return Map.copyOf(normalized);
    }

    private record ProductionModelAllocation(String modelKey, boolean enabled, double weight) {
    }
}
