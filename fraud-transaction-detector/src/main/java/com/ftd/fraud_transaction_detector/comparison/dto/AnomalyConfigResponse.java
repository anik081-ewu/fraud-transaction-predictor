package com.ftd.fraud_transaction_detector.comparison.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AnomalyConfigResponse(
        Long id,
        String configNo,
        String configName,
        List<String> enabledModels,
        String votingStrategy,
        Integer suspiciousVoteThreshold,
        Integer highRiskVoteThreshold,
        Integer mediumRiskVoteThreshold,
        Boolean gatingEnabled,
        Map<String, Object> gatingConfig,
        Long datasetPartitionId,
        String artifactBasePath,
        Boolean isActive,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
