package com.ftd.fraud_transaction_detector.aml.behaviour.domain;

import java.time.Instant;
import java.util.Map;

public record BehaviourScoringContext(
        String customerSegment,
        Instant scoredAt,
        Map<String, Object> attributes
) {
    public BehaviourScoringContext {
        customerSegment = customerSegment == null || customerSegment.isBlank()
                ? "GLOBAL"
                : customerSegment.trim();
        scoredAt = scoredAt == null ? Instant.now() : scoredAt;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
