package com.ftd.fraud_transaction_detector.comparison.dto;

import java.util.List;
import java.util.Map;

public record ComparisonPredictResponse(
        String transactionId,
        String accountId,
        Map<String, Map<String, Object>> modelResults,
        Map<String, Object> featureSummary,
        List<String> reasons
) {
}
