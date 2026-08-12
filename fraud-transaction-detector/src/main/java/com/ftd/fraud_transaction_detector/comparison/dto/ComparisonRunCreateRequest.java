package com.ftd.fraud_transaction_detector.comparison.dto;

import java.util.List;

public record ComparisonRunCreateRequest(
        Long uploadedDatasetId,
        Long scenarioSetId,
        List<Long> partitionIds,
        List<String> modelNames,
        String requestedBy
) {
}
