package com.ftd.fraud_transaction_detector.aml.rules.domain;

import com.ftd.fraud_transaction_detector.aml.scoring.domain.NormalizedScore;

import java.util.List;

public record RuleEngineResult(
        NormalizedScore score,
        RuleSeverity highestSeverity,
        boolean hardAlert,
        List<TriggeredRule> triggeredRules,
        List<String> reasonCodes
) {
    public RuleEngineResult {
        if (score == null) throw new IllegalArgumentException("score is required");
        if (highestSeverity == null) throw new IllegalArgumentException("highestSeverity is required");
        triggeredRules = triggeredRules == null ? List.of() : List.copyOf(triggeredRules);
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        if (hardAlert && triggeredRules.stream().noneMatch(TriggeredRule::hardOverride)) {
            throw new IllegalArgumentException("hardAlert requires at least one hard-override rule");
        }
    }
}
