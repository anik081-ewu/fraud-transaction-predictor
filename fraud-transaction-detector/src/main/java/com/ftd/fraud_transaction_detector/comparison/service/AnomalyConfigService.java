package com.ftd.fraud_transaction_detector.comparison.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.comparison.dto.AnomalyConfigRequest;
import com.ftd.fraud_transaction_detector.comparison.dto.AnomalyConfigResponse;
import com.ftd.fraud_transaction_detector.comparison.entity.AnomalyConfig;
import com.ftd.fraud_transaction_detector.comparison.repo.AnomalyConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AnomalyConfigService {

    private static final Set<String> SUPPORTED_MODELS = Set.of("IsolationForest", "Autoencoder", "BehavioralClusterOutlier");

    private final AnomalyConfigRepository anomalyConfigRepository;
    private final ObjectMapper objectMapper;

    public AnomalyConfigService(AnomalyConfigRepository anomalyConfigRepository, ObjectMapper objectMapper) {
        this.anomalyConfigRepository = anomalyConfigRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AnomalyConfigResponse> listConfigs() {
        return anomalyConfigRepository.findAllByOrderByCreatedAtDescIdDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AnomalyConfigResponse getActiveConfig() {
        return anomalyConfigRepository.findFirstByIsActiveTrueOrderByUpdatedAtDescIdDesc()
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional
    public AnomalyConfigResponse saveConfig(AnomalyConfigRequest request) {
        validateRequest(request);
        if (Boolean.TRUE.equals(request.isActive())) {
            deactivateOthers();
        }
        AnomalyConfig config = new AnomalyConfig();
        Instant now = Instant.now();
        config.setConfigNo("CFG-" + now.toEpochMilli());
        config.setConfigName(request.configName().trim());
        config.setEnabledModelsJson(toJsonSafe(request.enabledModels()));
        config.setVotingStrategy("SIMPLE_COUNT");
        config.setSuspiciousVoteThreshold(request.suspiciousVoteThreshold());
        config.setHighRiskVoteThreshold(request.enabledModels().size());
        config.setMediumRiskVoteThreshold(request.suspiciousVoteThreshold());
        config.setGatingEnabled(Boolean.FALSE);
        config.setGatingConfigJson(toJsonSafe(Map.of()));
        config.setDatasetPartitionId(request.datasetPartitionId());
        config.setArtifactBasePath(request.artifactBasePath());
        config.setIsActive(Boolean.TRUE.equals(request.isActive()));
        config.setCreatedBy(request.createdBy());
        config.setCreatedAt(now);
        config.setUpdatedAt(now);
        return toResponse(anomalyConfigRepository.save(config));
    }

    private void validateRequest(AnomalyConfigRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Config request is required");
        }
        if (request.configName() == null || request.configName().isBlank()) {
            throw new IllegalArgumentException("configName is required");
        }
        if (request.enabledModels() == null || request.enabledModels().isEmpty()) {
            throw new IllegalArgumentException("At least one enabled model is required");
        }
        if (request.enabledModels().stream().anyMatch(model -> !SUPPORTED_MODELS.contains(model))) {
            throw new IllegalArgumentException("One or more selected models are unsupported");
        }
        if (request.enabledModels().stream().distinct().count() != request.enabledModels().size()) {
            throw new IllegalArgumentException("Selected models must be unique");
        }
        if (request.suspiciousVoteThreshold() == null
                || request.suspiciousVoteThreshold() < 1
                || request.suspiciousVoteThreshold() > request.enabledModels().size()) {
            throw new IllegalArgumentException(
                    "Case vote threshold must be between 1 and " + request.enabledModels().size()
            );
        }
        if (request.artifactBasePath() == null || request.artifactBasePath().isBlank()) {
            throw new IllegalArgumentException("artifactBasePath is required");
        }
    }

    private void deactivateOthers() {
        anomalyConfigRepository.findAllByOrderByCreatedAtDescIdDesc().forEach(config -> {
            if (Boolean.TRUE.equals(config.getIsActive())) {
                config.setIsActive(Boolean.FALSE);
                config.setUpdatedAt(Instant.now());
                anomalyConfigRepository.save(config);
            }
        });
    }

    private AnomalyConfigResponse toResponse(AnomalyConfig config) {
        return new AnomalyConfigResponse(
                config.getId(),
                config.getConfigNo(),
                config.getConfigName(),
                readJsonList(config.getEnabledModelsJson()),
                config.getVotingStrategy(),
                config.getSuspiciousVoteThreshold(),
                config.getHighRiskVoteThreshold(),
                config.getMediumRiskVoteThreshold(),
                config.getGatingEnabled(),
                readJsonMap(config.getGatingConfigJson()),
                config.getDatasetPartitionId(),
                config.getArtifactBasePath(),
                config.getIsActive(),
                config.getCreatedBy(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }

    private String toJsonSafe(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize config", ex);
        }
    }

    private List<String> readJsonList(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private Map<String, Object> readJsonMap(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
