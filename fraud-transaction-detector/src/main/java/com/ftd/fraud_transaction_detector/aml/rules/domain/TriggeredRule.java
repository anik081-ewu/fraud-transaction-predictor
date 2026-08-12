package com.ftd.fraud_transaction_detector.aml.rules.domain;

import java.util.Map;

public record TriggeredRule(
        String ruleCode,
        RuleSeverity severity,
        double score,
        boolean hardOverride,
        Map<String, Object> evidence
) {
    public TriggeredRule(String ruleCode, RuleSeverity severity, double score, boolean hardOverride) {
        this(ruleCode, severity, score, hardOverride, Map.of());
    }

    public TriggeredRule {
        if (ruleCode == null || ruleCode.isBlank()) throw new IllegalArgumentException("ruleCode is required");
        if (severity == null) throw new IllegalArgumentException("severity is required");
        if (!Double.isFinite(score) || score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("rule score must be between 0.0 and 1.0");
        }
        ruleCode = ruleCode.trim();
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
    }
}
