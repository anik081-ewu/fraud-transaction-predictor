package com.ftd.fraud_transaction_detector.aml.rules.domain;

import java.time.Instant;
import java.util.Map;

public record RuleEvaluationContext(Instant evaluatedAt, Map<String, Object> attributes) {
    public RuleEvaluationContext {
        evaluatedAt = evaluatedAt == null ? Instant.now() : evaluatedAt;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
