package com.ftd.fraud_transaction_detector.aml.risk.application;

import com.ftd.fraud_transaction_detector.aml.behaviour.customer.CustomerBehaviourScore;
import com.ftd.fraud_transaction_detector.aml.behaviour.peer.PeerBehaviourScore;
import com.ftd.fraud_transaction_detector.aml.model.domain.MlModelScore;
import com.ftd.fraud_transaction_detector.aml.model.domain.MlModelScores;
import com.ftd.fraud_transaction_detector.aml.risk.domain.FinalRiskResult;
import com.ftd.fraud_transaction_detector.aml.risk.domain.RiskPolicy;
import com.ftd.fraud_transaction_detector.aml.rules.domain.RuleEngineResult;
import com.ftd.fraud_transaction_detector.aml.rules.domain.RuleSeverity;
import com.ftd.fraud_transaction_detector.aml.rules.domain.TriggeredRule;
import com.ftd.fraud_transaction_detector.aml.scoring.domain.NormalizedScore;
import com.ftd.fraud_transaction_detector.aml.scoring.domain.RiskBand;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeightedRiskAggregationEngineTest {

    private final WeightedRiskAggregationEngine engine = new WeightedRiskAggregationEngine();
    private final RiskPolicy policy = new RiskPolicy(
            "AML_RISK_POLICY_V3",
            0.20, 0.15, 0.40, 0.25,
            0.40, 0.65, 0.80
    );
    private final Map<String, Double> allocations = Map.of(
            "ISOLATION_FOREST", 1.0 / 3.0,
            "HALF_SPACE_TREES", 1.0 / 3.0,
            "ONLINE_ONE_CLASS_SVM", 1.0 / 3.0
    );

    @Test
    void appliesConfiguredWeightsToNormalizedComponentScores() {
        FinalRiskResult result = engine.aggregate(
                customer(0.80, "CUSTOMER_REASON"),
                peer(0.60, "PEER_REASON"),
                models(0.95, 0.90, 0.70),
                rules(0.50, false),
                policy,
                allocations
        );

        assertEquals(0.715, result.finalRiskScore(), 0.000001);
        assertEquals(RiskBand.MEDIUM, result.riskLevel());
        assertTrue(result.suspicious());
        assertEquals("AML_RISK_POLICY_V3", result.riskPolicyVersion());
        assertEquals(0.85, result.componentScores().mlEnsemble(), 0.000001);
        assertEquals(List.of("CUSTOMER_REASON", "HST_REASON", "IF_REASON", "OCSVM_REASON", "PEER_REASON", "RULE_REASON"),
                result.reasonCodes());
    }

    @Test
    void classifiesNormalAndLowScoresWithoutSuspicion() {
        FinalRiskResult normal = engine.aggregate(
                customer(0.10), peer(0.10), models(0.10, 0.10, 0.10), rules(0.10, false), policy, allocations
        );
        FinalRiskResult low = engine.aggregate(
                customer(0.50), peer(0.50), models(0.50, 0.50, 0.50), rules(0.50, false), policy, allocations
        );

        assertEquals(RiskBand.NORMAL, normal.riskLevel());
        assertEquals(RiskBand.LOW, low.riskLevel());
        assertFalse(normal.suspicious());
        assertFalse(low.suspicious());
    }

    @Test
    void hardRuleOverrideForcesAuditableHighRisk() {
        RuleEngineResult sanctions = new RuleEngineResult(
                score(0.20, "RULES_V1"), RuleSeverity.CRITICAL, true,
                List.of(new TriggeredRule("SANCTIONS_MATCH", RuleSeverity.CRITICAL, 1.0, true)),
                List.of("SANCTIONS_MATCH")
        );

        FinalRiskResult result = engine.aggregate(
                customer(0.0), peer(0.0), models(0.0, 0.0, 0.0), sanctions, policy, allocations
        );

        assertEquals(0.80, result.finalRiskScore());
        assertEquals(RiskBand.HIGH, result.riskLevel());
        assertTrue(result.suspicious());
        assertTrue(result.hardRuleOverride());
        assertTrue(result.reasonCodes().contains("HARD_RULE_OVERRIDE"));
        assertTrue(result.reasonCodes().contains("SANCTIONS_MATCH"));
    }

    @Test
    void unavailableModelsRemainZeroAndAreExplicitlyExplained() {
        FinalRiskResult result = engine.aggregate(
                customer(0.50), peer(0.50), new MlModelScores(Map.of()), rules(0.50, false), policy, allocations
        );

        assertEquals(0.30, result.finalRiskScore(), 0.000001);
        assertEquals(0.0, result.componentScores().mlEnsemble());
        assertTrue(result.reasonCodes().contains("ISOLATION_FOREST_SCORE_UNAVAILABLE"));
        assertTrue(result.reasonCodes().contains("HALF_SPACE_TREES_SCORE_UNAVAILABLE"));
        assertTrue(result.reasonCodes().contains("ONLINE_ONE_CLASS_SVM_SCORE_UNAVAILABLE"));
    }

    @Test
    void repeatedReasonsAreDeduplicatedAndSortedForReplay() {
        FinalRiskResult first = engine.aggregate(
                customer(0.5, "SHARED"), peer(0.5, "SHARED"),
                models(0.5, 0.5, 0.5), rules(0.5, false, "SHARED"), policy, allocations
        );
        FinalRiskResult replay = engine.aggregate(
                customer(0.5, "SHARED"), peer(0.5, "SHARED"),
                models(0.5, 0.5, 0.5), rules(0.5, false, "SHARED"), policy, allocations
        );

        assertEquals(first, replay);
        assertEquals(1, first.reasonCodes().stream().filter("SHARED"::equals).count());
        assertEquals(first.reasonCodes().stream().sorted().toList(), first.reasonCodes());
    }

    @Test
    void unanimousAnomalyVotesUseTheFullMlLayerWeight() {
        RiskPolicy mlLedPolicy = new RiskPolicy(
                "AML_RISK_POLICY_ML_LED", 0.24, 0.09, 0.65, 0.02,
                0.40, 0.65, 0.80
        );
        FinalRiskResult result = engine.aggregate(
                customer(0.0), peer(0.0), anomalousModels(1.0, 1.0, 0.61),
                rules(0.0, false), mlLedPolicy, allocations
        );

        assertEquals(1.0, result.componentScores().mlEnsemble());
        assertEquals(0.65, result.finalRiskScore(), 0.000001);
        assertEquals(RiskBand.MEDIUM, result.riskLevel());
        assertTrue(result.suspicious());
        assertTrue(result.reasonCodes().contains("ML_ENSEMBLE_UNANIMOUS_ANOMALY"));
    }

    private CustomerBehaviourScore customer(double value, String... reasons) {
        return new CustomerBehaviourScore(score(value, "CUSTOMER_V1"), band(value), 1.0, List.of(reasons));
    }

    private PeerBehaviourScore peer(double value, String... reasons) {
        return new PeerBehaviourScore("RETAIL", score(value, "PEER_V1"), band(value), 1.0, List.of(reasons));
    }

    private MlModelScores models(double isolationForest, double hst, double onlineSvm) {
        return models(isolationForest, hst, onlineSvm, false);
    }

    private MlModelScores anomalousModels(double isolationForest, double hst, double onlineSvm) {
        return models(isolationForest, hst, onlineSvm, true);
    }

    private MlModelScores models(double isolationForest, double hst, double onlineSvm, boolean anomaly) {
        return new MlModelScores(Map.of(
                "ISOLATION_FOREST",
                new MlModelScore(
                        "ISOLATION_FOREST", "IF-1",
                        score(isolationForest, "IF_V1"), anomaly, band(isolationForest), List.of("IF_REASON")
                ),
                "HALF_SPACE_TREES",
                new MlModelScore(
                        "HALF_SPACE_TREES", "HST-1",
                        score(hst, "HST_V1"), anomaly, band(hst), List.of("HST_REASON")
                ),
                "ONLINE_ONE_CLASS_SVM",
                new MlModelScore(
                        "ONLINE_ONE_CLASS_SVM", "OCSVM-1",
                        score(onlineSvm, "OCSVM_V1"), anomaly, band(onlineSvm), List.of("OCSVM_REASON")
                )
        ));
    }

    private RuleEngineResult rules(double value, boolean hardOverride, String... reasons) {
        List<String> reasonCodes = reasons.length == 0 ? List.of("RULE_REASON") : List.of(reasons);
        List<TriggeredRule> triggered = hardOverride
                ? List.of(new TriggeredRule("HARD_RULE", RuleSeverity.CRITICAL, 1.0, true))
                : List.of();
        return new RuleEngineResult(
                score(value, "RULES_V1"), hardOverride ? RuleSeverity.CRITICAL : RuleSeverity.MEDIUM,
                hardOverride, triggered, reasonCodes
        );
    }

    private NormalizedScore score(double value, String version) {
        return new NormalizedScore(value * 100.0, value, version);
    }

    private RiskBand band(double value) {
        if (value >= 0.80) return RiskBand.HIGH;
        if (value >= 0.65) return RiskBand.MEDIUM;
        if (value >= 0.40) return RiskBand.LOW;
        return RiskBand.NORMAL;
    }
}
