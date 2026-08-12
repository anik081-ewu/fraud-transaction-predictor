package com.ftd.fraud_transaction_detector.aml.research.api;

import java.util.List;
import java.util.Map;

public record LayerAblationReport(
        String status,
        long availableRows,
        List<Integer> partitionPercentages,
        List<String> variants,
        Map<String, Object> methodology,
        List<LayerAblationResult> results
) {
}
