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

@Service
public class ModelTuningConfigService {

    private static final List<Definition> DEFINITIONS = List.of(
            // Evaluation protocol — controls how all models are trained and compared
            definition("aml.research.minimum_rows", "200", "INTEGER", "Evaluation Protocol", "Minimum partition rows",
                    "Skip data partitions with fewer rows than this limit during comparison training.", 50.0, 100000.0, "50", List.of()),
            definition("aml.research.holdout_fraction", "0.20", "DECIMAL", "Evaluation Protocol", "Chronological holdout fraction",
                    "Newest fraction of each partition reserved for evaluation; the remainder trains each model.", 0.10, 0.40, "0.01", List.of()),
            definition("aml.research.maximum_evaluation_rows", "20000", "INTEGER", "Evaluation Protocol", "Evaluation row cap",
                    "Maximum holdout rows scored per model per partition during comparison.", 100.0, 1000000.0, "100", List.of()),
            definition("aml.research.random_seed", "42", "INTEGER", "Evaluation Protocol", "Random seed",
                    "Shared reproducibility seed for all model training and comparison runs.", 0.0, 2147483647.0, "1", List.of()),

            // Incremental models — trained continuously on live transaction windows
            definition("aml.hst.enabled", "true", "BOOLEAN", "Half-Space Trees", "Training enabled",
                    "Generate new Half-Space Trees candidates during training runs.", null, null, null, List.of()),
            definition("aml.hst.n_trees", "50", "INTEGER", "Half-Space Trees", "Number of trees",
                    "Ensemble size — more trees give more stable scores at higher memory cost.", 5.0, 500.0, "5", List.of()),
            definition("aml.hst.height", "8", "INTEGER", "Half-Space Trees", "Tree height",
                    "Maximum random partition depth per tree.", 3.0, 20.0, "1", List.of()),
            definition("aml.hst.window_size", "250", "INTEGER", "Half-Space Trees", "Streaming window size",
                    "Number of rows per reference/current mass update window.", 50.0, 100000.0, "50", List.of()),
            definition("aml.hst.threshold_quantile", "0.99", "DECIMAL", "Half-Space Trees", "Anomaly threshold quantile",
                    "Training score quantile used as the anomaly decision boundary.", 0.90, 0.9999, "0.001", List.of()),
            definition("aml.hst.parquet_batch_size", "65536", "INTEGER", "Half-Space Trees", "Parquet batch size",
                    "Feature rows decoded per Python batch during training.", 1000.0, 262144.0, "1024", List.of()),
            definition("aml.hst.seed", "42", "INTEGER", "Half-Space Trees", "Model seed",
                    "Reproducibility seed for tree structure initialisation.", 0.0, 2147483647.0, "1", List.of()),

            definition("aml.online_ocsvm.enabled", "true", "BOOLEAN", "Online One-Class SVM", "Training enabled",
                    "Generate new Online One-Class SVM candidates during training runs.", null, null, null, List.of()),
            definition("aml.online_ocsvm.nu", "0.05", "DECIMAL", "Online One-Class SVM", "Nu",
                    "Upper bound on the fraction of training errors and support vectors.", 0.001, 0.50, "0.001", List.of()),
            definition("aml.online_ocsvm.learning_rate", "0.01", "DECIMAL", "Online One-Class SVM", "Learning rate",
                    "Stochastic gradient step size for model weight updates.", 0.000001, 1.0, "0.001", List.of()),
            definition("aml.online_ocsvm.intercept_learning_rate", "0.01", "DECIMAL", "Online One-Class SVM", "Intercept learning rate",
                    "Stochastic gradient step size for the decision intercept.", 0.000001, 1.0, "0.001", List.of()),
            definition("aml.online_ocsvm.gamma", "0.5", "DECIMAL", "Online One-Class SVM", "RBF gamma",
                    "Random Fourier feature kernel width — controls sensitivity to feature distances.", 0.000001, 100.0, "0.01", List.of()),
            definition("aml.online_ocsvm.n_components", "64", "INTEGER", "Online One-Class SVM", "Random feature components",
                    "RBF approximation dimensions; higher values improve accuracy at higher CPU/memory cost.", 8.0, 2048.0, "8", List.of()),
            definition("aml.online_ocsvm.threshold_quantile", "0.99", "DECIMAL", "Online One-Class SVM", "Anomaly threshold quantile",
                    "Calibration score quantile used as the anomaly decision boundary.", 0.90, 0.9999, "0.001", List.of()),
            definition("aml.online_ocsvm.min_calibration_rows", "200", "INTEGER", "Online One-Class SVM", "Minimum calibration rows",
                    "Minimum learned rows before threshold calibration is attempted.", 20.0, 1000000.0, "10", List.of()),
            definition("aml.online_ocsvm.parquet_batch_size", "65536", "INTEGER", "Online One-Class SVM", "Parquet batch size",
                    "Feature rows decoded per Python batch during training.", 1000.0, 262144.0, "1024", List.of()),
            definition("aml.online_ocsvm.seed", "42", "INTEGER", "Online One-Class SVM", "Model seed",
                    "Reproducibility seed for random Fourier feature initialisation.", 0.0, 2147483647.0, "1", List.of()),

            // Batch models — trained on historical snapshots
            definition("aml.isolation_forest.enabled", "true", "BOOLEAN", "Isolation Forest", "Training enabled",
                    "Generate new Isolation Forest candidates during training runs.", null, null, null, List.of()),
            definition("aml.research.isolation_forest_n_estimators", "200", "INTEGER", "Isolation Forest", "Number of trees",
                    "More trees improve anomaly score stability at higher training time cost.", 50.0, 2000.0, "50", List.of()),
            definition("aml.research.isolation_forest_max_samples", "10000", "INTEGER", "Isolation Forest", "Max samples per tree",
                    "Rows sampled per tree — default 256 is too small for large datasets; 10,000 gives much stronger isolation signal.", 256.0, 100000.0, "256", List.of()),
            definition("aml.isolation_forest.threshold_quantile", "0.99", "DECIMAL", "Isolation Forest", "Anomaly threshold quantile",
                    "Training score quantile used as the anomaly decision boundary.", 0.90, 0.9999, "0.001", List.of()),
            definition("aml.research.isolation_forest_max_training_rows", "100000", "INTEGER", "Isolation Forest", "Training row cap",
                    "Maximum rows loaded from historical data for each training run.", 1000.0, 1000000.0, "1000", List.of()),
            definition("aml.isolation_forest.parquet_batch_size", "65536", "INTEGER", "Isolation Forest", "Parquet batch size",
                    "Feature rows decoded per Python batch during training.", 1000.0, 262144.0, "1024", List.of()),

            definition("aml.ocsvm_batch.enabled", "true", "BOOLEAN", "One-Class SVM", "Training enabled",
                    "Generate new batch One-Class SVM candidates during training runs.", null, null, null, List.of()),
            definition("aml.ocsvm_batch.nu", "0.05", "DECIMAL", "One-Class SVM", "Nu",
                    "Upper bound on the fraction of training errors and support vectors.", 0.001, 0.50, "0.001", List.of()),
            definition("aml.ocsvm_batch.threshold_quantile", "0.99", "DECIMAL", "One-Class SVM", "Anomaly threshold quantile",
                    "Training score quantile used as the anomaly decision boundary.", 0.90, 0.9999, "0.001", List.of()),
            definition("aml.research.ocsvm_max_training_rows", "1000000", "INTEGER", "One-Class SVM", "Training row cap",
                    "Maximum rows used for fitting — set high to train on all available data.", 1000.0, 10000000.0, "1000", List.of()),
            definition("aml.ocsvm_batch.parquet_batch_size", "65536", "INTEGER", "One-Class SVM", "Parquet batch size",
                    "Feature rows decoded per Python batch during training.", 1000.0, 262144.0, "1024", List.of()),

            definition("aml.autoencoder.enabled", "true", "BOOLEAN", "Autoencoder", "Training enabled",
                    "Generate new Autoencoder candidates during training runs.", null, null, null, List.of()),
            definition("aml.research.autoencoder_hidden_layer_sizes", "32,8,32", "TEXT", "Autoencoder", "Hidden layer sizes",
                    "Comma-separated neuron counts per layer. The middle value is the bottleneck — smaller forces tighter compression and stronger anomaly signal.", null, null, null, List.of()),
            definition("aml.research.autoencoder_max_iter", "200", "INTEGER", "Autoencoder", "Max training iterations",
                    "Maximum gradient descent epochs — early stopping may terminate training before this limit.", 10.0, 2000.0, "10", List.of()),
            definition("aml.research.autoencoder_max_training_rows", "50000", "INTEGER", "Autoencoder", "Training row cap",
                    "Maximum rows used for fitting the autoencoder.", 1000.0, 1000000.0, "1000", List.of())
    );

    private final AppConfigRepository appConfigRepository;

    public ModelTuningConfigService(AppConfigRepository appConfigRepository) {
        this.appConfigRepository = appConfigRepository;
    }

    @Transactional(readOnly = true)
    public List<ModelTuningItemResponse> list() {
        return DEFINITIONS.stream()
                .map(definition -> toResponse(
                        definition,
                        appConfigRepository.findById(definition.key())
                                .map(AppConfig::getConfigValue)
                                .orElse(definition.defaultValue())
                ))
                .toList();
    }

    @Transactional
    public List<ModelTuningItemResponse> update(ModelTuningUpdateRequest request) {
        if (request == null || request.values() == null || request.values().isEmpty()) {
            throw new IllegalArgumentException("Model tuning values are required");
        }

        Map<String, Definition> definitionsByKey = new LinkedHashMap<>();
        DEFINITIONS.forEach(definition -> definitionsByKey.put(definition.key(), definition));
        for (Map.Entry<String, String> entry : request.values().entrySet()) {
            Definition definition = definitionsByKey.get(entry.getKey());
            if (definition == null) {
                throw new IllegalArgumentException("Unsupported model tuning key: " + entry.getKey());
            }
            String normalizedValue = validateAndNormalize(definition, entry.getValue());
            AppConfig config = appConfigRepository.findById(definition.key()).orElseGet(AppConfig::new);
            config.setConfigKey(definition.key());
            config.setConfigValue(normalizedValue);
            config.setValueType(definition.type());
            config.setDescription(definition.description());
            config.setUpdatedAt(Instant.now());
            appConfigRepository.save(config);
        }
        return list();
    }

    private String validateAndNormalize(Definition definition, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException(definition.label() + " is required");
        }
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
            case "SELECT" -> {
                if (!definition.options().contains(value)) {
                    throw new IllegalArgumentException(
                            definition.label() + " must be one of: " + String.join(", ", definition.options())
                    );
                }
                yield value;
            }
            default -> throw new IllegalArgumentException("Unsupported value type: " + definition.type());
        };
    }

    private void validateRange(Definition definition, double value) {
        if (!Double.isFinite(value)
                || definition.minValue() != null && value < definition.minValue()
                || definition.maxValue() != null && value > definition.maxValue()) {
            throw new IllegalArgumentException(
                    definition.label() + " must be between " + definition.minValue() + " and " + definition.maxValue()
            );
        }
    }

    private ModelTuningItemResponse toResponse(Definition definition, String value) {
        return new ModelTuningItemResponse(
                definition.key(),
                value,
                definition.type(),
                definition.group(),
                definition.label(),
                definition.description(),
                definition.minValue(),
                definition.maxValue(),
                definition.step(),
                definition.options()
        );
    }

    private static Definition definition(
            String key,
            String defaultValue,
            String type,
            String group,
            String label,
            String description,
            Double minValue,
            Double maxValue,
            String step,
            List<String> options
    ) {
        return new Definition(
                key, defaultValue, type, group, label, description, minValue, maxValue, step, options
        );
    }

    private record Definition(
            String key,
            String defaultValue,
            String type,
            String group,
            String label,
            String description,
            Double minValue,
            Double maxValue,
            String step,
            List<String> options
    ) {
    }
}
