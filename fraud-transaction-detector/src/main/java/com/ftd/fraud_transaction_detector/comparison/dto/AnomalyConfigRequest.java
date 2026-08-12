package com.ftd.fraud_transaction_detector.comparison.dto;

import java.util.List;
import java.util.Map;

public record AnomalyConfigRequest(
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
        String createdBy
) {
}
