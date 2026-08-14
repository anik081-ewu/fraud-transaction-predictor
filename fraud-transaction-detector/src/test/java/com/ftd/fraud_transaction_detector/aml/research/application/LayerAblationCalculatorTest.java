package com.ftd.fraud_transaction_detector.aml.research.application;

import com.ftd.fraud_transaction_detector.aml.research.domain.AblationVariant;
import com.ftd.fraud_transaction_detector.aml.research.domain.CounterfactualRisk;
import com.ftd.fraud_transaction_detector.aml.research.domain.LayerScores;
import com.ftd.fraud_transaction_detector.aml.risk.domain.RiskPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class LayerAblationCalculatorTest {

    private final LayerAblationCalculator calculator = new LayerAblationCalculator();
    private final RiskPolicy policy = new RiskPolicy(
            "TEST", 0.20, 0.20, 0.30, 0.30, 0.30, 0.60, 0.80
    );

    @Test
    void removingRulesRenormalizesRemainingWeightsAndRemovesHardOverride() {
        LayerScores scores = new LayerScores(0.20, 0.30, 0.60, 1.0, true);

        CounterfactualRisk full = calculator.calculate(scores, policy, AblationVariant.FULL);
        CounterfactualRisk withoutRules = calculator.calculate(scores, policy, AblationVariant.WITHOUT_RULES);

        assertThat(full.hardRuleOverride()).isTrue();
        assertThat(full.score()).isEqualTo(0.80);
        assertThat(withoutRules.hardRuleOverride()).isFalse();
        assertThat(withoutRules.score()).isCloseTo(0.40, offset(0.000001));
        assertThat(withoutRules.suspicious()).isFalse();
    }

    @Test
    void rulesOnlyShowsDirectRuleImpact() {
        CounterfactualRisk result = calculator.calculate(
                new LayerScores(0.0, 0.0, 0.0, 0.70, false), policy, AblationVariant.RULES_ONLY
        );

        assertThat(result.score()).isCloseTo(0.70, offset(0.000001));
        assertThat(result.suspicious()).isTrue();
    }
}
