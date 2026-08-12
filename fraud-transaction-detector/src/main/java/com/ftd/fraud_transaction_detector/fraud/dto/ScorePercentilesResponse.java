package com.ftd.fraud_transaction_detector.fraud.dto;

import java.util.List;

public record ScorePercentilesResponse(
        List<Integer> percentiles,
        List<Double> lofDecision,
        List<Double> svmDecision
) {
}

