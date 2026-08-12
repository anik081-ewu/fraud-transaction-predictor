package com.ftd.fraud_transaction_detector.comparison.dto;

import java.util.List;

public record PartitionTrainingRequest(
        List<String> modelNames,
        String requestedBy
) {
}
