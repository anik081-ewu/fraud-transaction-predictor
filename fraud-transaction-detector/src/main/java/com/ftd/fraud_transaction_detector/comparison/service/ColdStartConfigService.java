package com.ftd.fraud_transaction_detector.comparison.service;

import com.ftd.fraud_transaction_detector.comparison.dto.ColdStartConfigItemResponse;
import com.ftd.fraud_transaction_detector.comparison.dto.ColdStartConfigUpdateRequest;
import com.ftd.fraud_transaction_detector.config.entity.AppConfig;
import com.ftd.fraud_transaction_detector.config.repo.AppConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ColdStartConfigService {

    private static final List<String> KEYS = List.of(
            "ml.cold_start.enabled",
            "ml.min_transaction_count_before_predict"
    );

    private final AppConfigRepository appConfigRepository;

    public ColdStartConfigService(AppConfigRepository appConfigRepository) {
        this.appConfigRepository = appConfigRepository;
    }

    @Transactional(readOnly = true)
    public List<ColdStartConfigItemResponse> list() {
        return KEYS.stream()
                .map(key -> appConfigRepository.findById(key).orElseGet(() -> defaultConfig(key)))
                .map(config -> new ColdStartConfigItemResponse(
                        config.getConfigKey(),
                        config.getConfigValue(),
                        config.getValueType(),
                        config.getDescription()
                ))
                .toList();
    }

    @Transactional
    public List<ColdStartConfigItemResponse> update(ColdStartConfigUpdateRequest request) {
        if (request == null || request.values() == null || request.values().isEmpty()) {
            throw new IllegalArgumentException("Cold start values are required");
        }
        for (Map.Entry<String, String> entry : request.values().entrySet()) {
            if (!KEYS.contains(entry.getKey())) {
                continue;
            }
            AppConfig config = appConfigRepository.findById(entry.getKey()).orElseGet(() -> defaultConfig(entry.getKey()));
            config.setConfigValue(entry.getValue());
            config.setUpdatedAt(Instant.now());
            appConfigRepository.save(config);
        }
        return list();
    }

    private AppConfig defaultConfig(String key) {
        AppConfig config = new AppConfig();
        config.setConfigKey(key);
        config.setUpdatedAt(Instant.now());
        return switch (key) {
            case "ml.cold_start.enabled" -> with(config, "true", "BOOLEAN", "Enable or disable cold start handling. When disabled all accounts go through ML scoring regardless of history.");
            case "ml.min_transaction_count_before_predict" -> with(config, "20", "INTEGER", "Minimum prior transaction count required before the 4-layer engine scores a transaction. Accounts below this threshold receive NORMAL/ALLOW.");
            default -> with(config, "", "STRING", "Cold start config");
        };
    }

    private AppConfig with(AppConfig config, String value, String type, String description) {
        config.setConfigValue(value);
        config.setValueType(type);
        config.setDescription(description);
        return config;
    }
}
