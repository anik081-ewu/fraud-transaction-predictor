package com.ftd.fraud_transaction_detector.comparison.dto;

import java.util.List;
import java.util.Map;

public record ComparisonRunDetailResponse(
        ComparisonRunResponse run,
        List<ComparisonResultResponse> results,
        Map<String, Object> summary
) {
}
