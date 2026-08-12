package com.ftd.fraud_transaction_detector.comparison.dto;

import java.util.List;

public record ModelTuningItemResponse(
        String configKey,
        String configValue,
        String valueType,
        String groupName,
        String displayName,
        String description,
        Double minValue,
        Double maxValue,
        String step,
        List<String> options
) {
}
