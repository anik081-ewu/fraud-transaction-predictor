package com.ftd.fraud_transaction_detector.comparison.service;

import com.ftd.fraud_transaction_detector.comparison.dto.ModelTuningItemResponse;
import com.ftd.fraud_transaction_detector.comparison.dto.ModelTuningUpdateRequest;
import com.ftd.fraud_transaction_detector.config.entity.AppConfig;
import com.ftd.fraud_transaction_detector.config.repo.AppConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class ModelTuningConfigService {

    private static final List<Definition> COMMON_DEFINITIONS = List.of(
            definition("aml.research.minimum_rows", "200", "INTEGER", "Evaluation Protocol", "Minimum partition rows",
                    "Skip comparison partitions with fewer rows than this limit.", 50.0, 100000.0, "50"),
            definition("aml.research.holdout_fraction", "0.20", "DECIMAL", "Evaluation Protocol", "Chronological holdout fraction",
                    "Newest fraction of each partition reserved for evaluation.", 0.10, 0.40, "0.01"),
            definition("aml.research.maximum_evaluation_rows", "20000", "INTEGER", "Evaluation Protocol", "Evaluation row cap",
                    "Maximum evaluation rows scored per model and partition.", 100.0, 1000000.0, "100"),
            definition("aml.research.random_seed", "42", "INTEGER", "Evaluation Protocol", "Random seed",
                    "Shared reproducibility seed for training and comparison.", 0.0, 2147483647.0, "1")
    );

    private static final List<Definition> UNSUPERVISED_DEFINITIONS = List.of(
            definition("aml.isolation_forest.enabled", "true", "BOOLEAN", "Isolation Forest", "Training enabled",
                    "Generate new Isolation Forest candidates.", null, null, null),
            definition("aml.research.isolation_forest_n_estimators", "200", "INTEGER", "Isolation Forest", "Number of trees",
                    "More trees improve score stability at additional training cost.", 50.0, 2000.0, "50"),
            definition("aml.research.isolation_forest_max_samples", "10000", "INTEGER", "Isolation Forest", "Max samples per tree",
                    "Rows sampled per isolation tree.", 256.0, 100000.0, "256"),
            definition("aml.autoencoder.enabled", "true", "BOOLEAN", "Autoencoder", "Training enabled",
                    "Generate new Autoencoder candidates.", null, null, null),
            definition("aml.research.autoencoder_hidden_layer_sizes", "32,8,32", "TEXT", "Autoencoder", "Hidden layer sizes",
                    "Comma-separated neuron counts; the middle layer is the bottleneck.", null, null, null),
            definition("aml.research.autoencoder_max_iter", "200", "INTEGER", "Autoencoder", "Max training iterations",
                    "Maximum optimization iterations before training stops.", 10.0, 2000.0, "10"),
            definition("aml.cluster_outlier.enabled", "true", "BOOLEAN", "Behavioral Cluster Outlier", "Training enabled",
                    "Generate cluster-conditional behavioural outlier candidates.", null, null, null),
            definition("ml.cluster_outlier.n_clusters", "16", "INTEGER", "Behavioral Cluster Outlier", "Behaviour groups",
                    "Number of broad transaction-behaviour groups learned before outlier scoring.", 2.0, 128.0, "1"),
            definition("ml.cluster_outlier.batch_size", "2048", "INTEGER", "Behavioral Cluster Outlier", "Cluster batch size",
                    "Rows processed per MiniBatch K-Means update.", 128.0, 65536.0, "128"),
            definition("ml.cluster_outlier.contamination", "0.01", "DECIMAL", "Behavioral Cluster Outlier", "Expected anomaly rate",
                    "Expected cluster-conditional outlier fraction used for the decision boundary.", 0.001, 0.25, "0.001")
    );

    private static final List<Definition> SUPERVISED_DEFINITIONS = List.of(
            definition("ml.xgboost.enabled", "true", "BOOLEAN", "XGBoost", "Training enabled",
                    "Generate new XGBoost fraud-classifier candidates.", null, null, null),
            definition("ml.xgboost.n_estimators", "500", "INTEGER", "XGBoost", "Boosting trees",
                    "Number of sequential boosted trees.", 50.0, 2000.0, "50"),
            definition("ml.xgboost.max_depth", "4", "INTEGER", "XGBoost", "Maximum tree depth",
                    "Maximum interaction depth of each tree.", 2.0, 16.0, "1"),
            definition("ml.xgboost.learning_rate", "0.03", "DECIMAL", "XGBoost", "Learning rate",
                    "Contribution of each new tree.", 0.005, 0.5, "0.005"),
            definition("ml.xgboost.subsample", "0.85", "DECIMAL", "XGBoost", "Row sample fraction",
                    "Fraction of labelled rows sampled for each tree.", 0.5, 1.0, "0.05"),
            definition("ml.xgboost.colsample_bytree", "0.85", "DECIMAL", "XGBoost", "Feature sample fraction",
                    "Fraction of features sampled for each tree.", 0.5, 1.0, "0.05"),
            definition("ml.xgboost.min_child_weight", "3.0", "DECIMAL", "XGBoost", "Minimum child weight",
                    "Higher values reduce overfitting in small fraud regions.", 1.0, 50.0, "1"),
            definition("ml.xgboost.reg_alpha", "0.05", "DECIMAL", "XGBoost", "L1 regularization",
                    "Penalizes weak or noisy feature effects.", 0.0, 10.0, "0.05"),
            definition("ml.xgboost.reg_lambda", "2.0", "DECIMAL", "XGBoost", "L2 regularization",
                    "Stabilizes boosted-tree weights on imbalanced data.", 0.0, 20.0, "0.5"),
            definition("ml.random_forest.enabled", "true", "BOOLEAN", "Class-Balanced Random Forest", "Training enabled",
                    "Generate class-balanced Random Forest fraud-classifier candidates.", null, null, null),
            definition("ml.random_forest.n_estimators", "500", "INTEGER", "Class-Balanced Random Forest", "Number of trees",
                    "Number of independently trained trees.", 50.0, 2000.0, "50"),
            definition("ml.random_forest.max_depth", "16", "INTEGER", "Class-Balanced Random Forest", "Maximum tree depth",
                    "Limits tree complexity and memorization.", 3.0, 50.0, "1"),
            definition("ml.random_forest.min_samples_leaf", "2", "INTEGER", "Class-Balanced Random Forest", "Minimum leaf rows",
                    "Minimum labelled rows in each terminal leaf.", 1.0, 100.0, "1"),
            definition("ml.extra_trees.enabled", "true", "BOOLEAN", "Extra Trees", "Training enabled",
                    "Generate highly randomized tree-ensemble fraud-classifier candidates.", null, null, null),
            definition("ml.extra_trees.n_estimators", "400", "INTEGER", "Extra Trees", "Number of trees",
                    "More randomized trees improve ranking stability at additional training cost.", 100.0, 2000.0, "50"),
            definition("ml.extra_trees.min_samples_leaf", "2", "INTEGER", "Extra Trees", "Minimum leaf rows",
                    "Controls variance and prevents tiny memorized fraud regions.", 1.0, 100.0, "1"),
            definition("ml.extra_trees.max_features", "0.8", "DECIMAL", "Extra Trees", "Feature sample fraction",
                    "Fraction of features considered at each randomized split.", 0.1, 1.0, "0.1"),
            definition("ml.supervised.tuning_enabled", "true", "BOOLEAN", "Evaluation Protocol", "Automatic model tuning",
                    "Evaluate leakage-safe hyperparameter candidates before fitting each final classifier.", null, null, null),
            definition("ml.supervised.tuning_candidates", "12", "INTEGER", "Evaluation Protocol", "Maximum tuning candidates",
                    "Maximum deterministic hyperparameter candidates evaluated for each model.", 1.0, 20.0, "1"),
            definition("ml.supervised.class_weight_multiplier", "1.0", "DECIMAL", "Label Quality", "Fraud class weighting",
                    "Starting strength for fraud-class imbalance correction; automatic tuning explores safer lower weights too.", 0.10, 3.0, "0.05"),
            definition("ml.supervised.auto_no_case_weight", "0.35", "DECIMAL", "Label Quality", "Automatic valid-label weight",
                    "Training confidence assigned to API transactions labelled valid because no case was created.", 0.05, 1.0, "0.05"),
            definition("ml.supervised.threshold_beta", "1.0", "DECIMAL", "Decision Calibration", "Recall emphasis",
                    "Fallback F-beta used only when the minimum precision target cannot be reached.", 0.5, 3.0, "0.1"),
            definition("ml.supervised.minimum_precision", "0.0", "DECIMAL", "Decision Calibration", "Optional minimum precision",
                    "Use 0 for unbiased F-beta calibration. Set a value such as 0.80 only when the operating policy requires a precision floor.", 0.0, 0.99, "0.01")
    );

    private final AppConfigRepository appConfigRepository;

    public ModelTuningConfigService(AppConfigRepository appConfigRepository) {
        this.appConfigRepository = appConfigRepository;
    }

    @Transactional(readOnly = true)
    public List<ModelTuningItemResponse> list() {
        return activeDefinitions().stream()
                .map(definition -> toResponse(definition, appConfigRepository.findById(definition.key())
                        .map(AppConfig::getConfigValue).orElse(definition.defaultValue())))
                .toList();
    }

    @Transactional
    public List<ModelTuningItemResponse> update(ModelTuningUpdateRequest request) {
        if (request == null || request.values() == null || request.values().isEmpty()) {
            throw new IllegalArgumentException("Model tuning values are required");
        }
        Map<String, Definition> definitions = new LinkedHashMap<>();
        activeDefinitions().forEach(definition -> definitions.put(definition.key(), definition));
        for (Map.Entry<String, String> entry : request.values().entrySet()) {
            Definition definition = definitions.get(entry.getKey());
            if (definition == null) {
                throw new IllegalArgumentException("Unsupported tuning key for the active system type: " + entry.getKey());
            }
            AppConfig config = appConfigRepository.findById(definition.key()).orElseGet(AppConfig::new);
            config.setConfigKey(definition.key());
            config.setConfigValue(validateAndNormalize(definition, entry.getValue()));
            config.setValueType(definition.type());
            config.setDescription(definition.description());
            config.setUpdatedAt(Instant.now());
            appConfigRepository.save(config);
        }
        return list();
    }

    private List<Definition> activeDefinitions() {
        boolean supervised = appConfigRepository.findById("system.learning_mode")
                .map(AppConfig::getConfigValue).map(String::trim)
                .map("SUPERVISED"::equalsIgnoreCase).orElse(false);
        return Stream.concat(COMMON_DEFINITIONS.stream(),
                (supervised ? SUPERVISED_DEFINITIONS : UNSUPERVISED_DEFINITIONS).stream()).toList();
    }

    private String validateAndNormalize(Definition definition, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) throw new IllegalArgumentException(definition.label() + " is required");
        String value = rawValue.trim();
        return switch (definition.type()) {
            case "BOOLEAN" -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException(definition.label() + " must be true or false");
                }
                yield value.toLowerCase();
            }
            case "INTEGER" -> {
                try {
                    long parsed = Long.parseLong(value);
                    validateRange(definition, parsed);
                    yield Long.toString(parsed);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException(definition.label() + " must be an integer");
                }
            }
            case "DECIMAL" -> {
                try {
                    BigDecimal parsed = new BigDecimal(value);
                    validateRange(definition, parsed.doubleValue());
                    yield parsed.stripTrailingZeros().toPlainString();
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException(definition.label() + " must be numeric");
                }
            }
            case "TEXT" -> value;
            default -> throw new IllegalArgumentException("Unsupported value type: " + definition.type());
        };
    }

    private void validateRange(Definition definition, double value) {
        if (!Double.isFinite(value)
                || definition.minValue() != null && value < definition.minValue()
                || definition.maxValue() != null && value > definition.maxValue()) {
            throw new IllegalArgumentException(definition.label() + " must be between "
                    + definition.minValue() + " and " + definition.maxValue());
        }
    }

    private ModelTuningItemResponse toResponse(Definition definition, String value) {
        return new ModelTuningItemResponse(definition.key(), value, definition.type(), definition.group(),
                definition.label(), definition.description(), definition.minValue(), definition.maxValue(),
                definition.step(), List.of());
    }

    private static Definition definition(String key, String defaultValue, String type, String group,
                                         String label, String description, Double minValue,
                                         Double maxValue, String step) {
        return new Definition(key, defaultValue, type, group, label, description, minValue, maxValue, step);
    }

    private record Definition(String key, String defaultValue, String type, String group, String label,
                              String description, Double minValue, Double maxValue, String step) {}
}
