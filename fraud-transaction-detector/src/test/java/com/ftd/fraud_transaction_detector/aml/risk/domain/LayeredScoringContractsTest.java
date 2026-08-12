package com.ftd.fraud_transaction_detector.aml.risk.domain;

import com.ftd.fraud_transaction_detector.aml.rules.domain.RuleEngineResult;
import com.ftd.fraud_transaction_detector.aml.rules.domain.RuleSeverity;
import com.ftd.fraud_transaction_detector.aml.rules.domain.TriggeredRule;
import com.ftd.fraud_transaction_detector.aml.scoring.domain.NormalizedScore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayeredScoringContractsTest {

    @Test
    void acceptsNormalizedComponentScore() {
        NormalizedScore score = new NormalizedScore(4.724, 0.89, "HST_QUANTILE_V1");

        assertEquals(4.724, score.rawScore());
        assertEquals(0.89, score.normalizedScore());
        assertEquals("HST_QUANTILE_V1", score.normalizationVersion());
    }

    @Test
    void rejectsScoreOutsideCommonRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new NormalizedScore(-0.42, 1.01, "OCSVM_MARGIN_V1")
        );
    }

    @Test
    void requiresRiskPolicyWeightsToSumToOne() {
        assertThrows(IllegalArgumentException.class, () -> new RiskPolicy(
                "AML_RISK_POLICY_V2",
                0.20, 0.15, 0.10, 0.25, 0.15, 0.20,
                0.40, 0.65, 0.80
        ));
    }

    @Test
    void acceptsVersionedRiskPolicyContract() {
        RiskPolicy policy = new RiskPolicy(
                "AML_RISK_POLICY_V2",
                0.20, 0.15, 0.00, 0.25, 0.15, 0.25,
                0.40, 0.65, 0.80
        );

        assertEquals("AML_RISK_POLICY_V2", policy.version());
        assertEquals(0.80, policy.highRiskThreshold());
    }

    @Test
    void hardAlertRequiresAnExplicitOverrideRule() {
        assertThrows(IllegalArgumentException.class, () -> new RuleEngineResult(
                new NormalizedScore(0.92, 0.92, "AML_RULES_V1"),
                RuleSeverity.CRITICAL,
                true,
                List.of(new TriggeredRule("HIGH_TRANSACTION_COUNT_1H", RuleSeverity.HIGH, 0.85, false)),
                List.of("HIGH_TRANSACTION_COUNT_1H")
        ));

        RuleEngineResult result = new RuleEngineResult(
                new NormalizedScore(1.0, 1.0, "AML_RULES_V1"),
                RuleSeverity.CRITICAL,
                true,
                List.of(new TriggeredRule("SANCTIONS_MATCH", RuleSeverity.CRITICAL, 1.0, true)),
                List.of("SANCTIONS_MATCH")
        );

        assertTrue(result.hardAlert());
    }
}
