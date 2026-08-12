package com.ftd.fraud_transaction_detector.comparison.dto;

import java.util.Map;

public record ColdStartConfigUpdateRequest(
        Map<String, String> values
) {
}
