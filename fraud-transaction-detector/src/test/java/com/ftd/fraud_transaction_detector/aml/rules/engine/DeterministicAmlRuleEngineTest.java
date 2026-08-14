package com.ftd.fraud_transaction_detector.aml.rules.engine;

import com.ftd.fraud_transaction_detector.aml.feature.domain.AmountFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.BehaviorFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.NoveltyFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.PeerFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.ProfileFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.ProfileStatus;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TimeFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TransactionFeatureVector;
import com.ftd.fraud_transaction_detector.aml.feature.domain.VelocityFeatures;
import com.ftd.fraud_transaction_detector.aml.rules.domain.RuleEngineResult;
import com.ftd.fraud_transaction_detector.aml.rules.domain.RuleEvaluationContext;
import com.ftd.fraud_transaction_detector.aml.rules.domain.RuleSeverity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicAmlRuleEngineTest {

    private final DeterministicAmlRuleEngine engine = new DeterministicAmlRuleEngine(
            DeterministicAmlRulePolicy.transparentV1()
    );

    @Test
    void returnsNoRuleEvidenceForNormalScenario() {
        RuleEngineResult result = engine.evaluate(normalVector(), context(Map.of()));

        assertEquals(0.0, result.score().normalizedScore());
        assertEquals(RuleSeverity.NONE, result.highestSeverity());
        assertFalse(result.hardAlert());
        assertTrue(result.triggeredRules().isEmpty());
    }

    @Test
    void detectsRapidMultiBeneficiaryRepeatedVelocity() {
        TransactionFeatureVector features = vector(
                amount(1.0, 0.10),
                velocity(5, 10, 4, 4, 0, 0.0),
                novelty(false, false, false, false, false),
                peer(0.02)
        );

        RuleEngineResult result = engine.evaluate(features, context(Map.of()));

        assertEquals(RuleSeverity.HIGH, result.highestSeverity());
        assertTrue(result.reasonCodes().contains("RAPID_TRANSACTION_VELOCITY_10M"));
        assertTrue(result.reasonCodes().contains("HIGH_TRANSACTION_VELOCITY_1H"));
        assertTrue(result.reasonCodes().contains("MULTIPLE_BENEFICIARIES_1H"));
        assertTrue(result.reasonCodes().contains("REPEATED_AMOUNT_PATTERN_24H"));
        assertTrue(result.score().normalizedScore() > 0.95);
    }

    @Test
    void detectsStructuringAgainstContextThresholdAndPreservesEvidence() {
        TransactionFeatureVector features = vector(
                amount(1.0, 0.10),
                velocity(1, 3, 1, 1, 3, 12_500.0),
                novelty(false, false, false, false, false),
                peer(0.02)
        );

        RuleEngineResult result = engine.evaluate(
                features,
                context(Map.of(DeterministicAmlRuleEngine.REPORTING_THRESHOLD_ATTRIBUTE, 10_000.0))
        );

        var rule = result.triggeredRules().stream()
                .filter(item -> item.ruleCode().equals("POTENTIAL_STRUCTURING_24H"))
                .findFirst()
                .orElseThrow();
        assertEquals(3, rule.evidence().get("belowThresholdCount24Hours"));
        assertEquals(12_500.0, rule.evidence().get("belowThresholdAmountSum24Hours"));
        assertFalse(rule.hardOverride());
    }

    @Test
    void combinesNewBeneficiaryDeviceAndUnusualHour() {
        TransactionFeatureVector features = vector(
                amount(1.0, 0.10),
                velocity(1, 1, 1, 1, 0, 0.0),
                novelty(true, false, false, true, true),
                peer(0.02)
        );

        RuleEngineResult result = engine.evaluate(features, context(Map.of()));

        assertEquals(RuleSeverity.HIGH, result.highestSeverity());
        assertEquals(1, result.triggeredRules().size());
        assertEquals("NEW_BENEFICIARY_DEVICE_AT_UNUSUAL_HOUR", result.triggeredRules().get(0).ruleCode());
    }

    @Test
    void sanctionsScreeningCreatesCriticalHardOverride() {
        RuleEngineResult result = engine.evaluate(
                normalVector(),
                context(Map.of(DeterministicAmlRuleEngine.SANCTIONS_MATCH_ATTRIBUTE, true))
        );

        assertEquals(1.0, result.score().normalizedScore());
        assertEquals(RuleSeverity.CRITICAL, result.highestSeverity());
        assertTrue(result.hardAlert());
        assertEquals("SANCTIONS_MATCH", result.triggeredRules().get(0).ruleCode());
    }

    @Test
    void scenarioReplayIsDeterministicAndRuleOrderIsStable() {
        TransactionFeatureVector scenario = vector(
                amount(9.0, 0.90),
                velocity(5, 10, 4, 4, 3, 12_500.0),
                novelty(true, true, false, true, true),
                peer(0.60)
        );
        RuleEvaluationContext context = context(Map.of(
                DeterministicAmlRuleEngine.REPORTING_THRESHOLD_ATTRIBUTE, 10_000.0
        ));

        RuleEngineResult first = engine.evaluate(scenario, context);
        RuleEngineResult replay = engine.evaluate(scenario, context);

        assertEquals(first, replay);
        assertEquals(
                first.reasonCodes().stream().sorted().toList(),
                first.reasonCodes()
        );
    }

    private TransactionFeatureVector normalVector() {
        return vector(
                amount(1.0, 0.10),
                velocity(1, 1, 1, 1, 0, 0.0),
                novelty(false, false, false, false, false),
                peer(0.02)
        );
    }

    private RuleEvaluationContext context(Map<String, Object> attributes) {
        return new RuleEvaluationContext(Instant.parse("2026-08-05T00:00:00Z"), attributes);
    }

    private AmountFeatures amount(double customerRatio, double balanceRatio) {
        return new AmountFeatures(
                customerRatio * 100.0, 10_000.0, balanceRatio,
                100.0, 100.0, 100.0, 100.0, 10.0, 130.0, 80.0,
                customerRatio, customerRatio, customerRatio - 1.0
        );
    }

    private VelocityFeatures velocity(
            int count10Minutes,
            int count1Hour,
            int beneficiaries1Hour,
            int repeatedAmounts24Hours,
            int belowThresholdCount,
            double belowThresholdSum
    ) {
        return new VelocityFeatures(
                count10Minutes, count1Hour, Math.max(count1Hour, belowThresholdCount),
                Math.max(count1Hour, belowThresholdCount), Math.max(count1Hour, belowThresholdCount),
                100, 100, 100, 100, 100,
                beneficiaries1Hour, beneficiaries1Hour, beneficiaries1Hour,
                repeatedAmounts24Hours, belowThresholdCount, belowThresholdSum
        );
    }

    private NoveltyFeatures novelty(
            boolean beneficiary,
            boolean location,
            boolean channel,
            boolean device,
            boolean unusualHour
    ) {
        return new NoveltyFeatures(beneficiary, location, channel, device, unusualHour);
    }

    private PeerFeatures peer(double turnoverRatio) {
        return new PeerFeatures(
                "RETAIL_SALARIED", 100.0, 100.0, 20.0,
                1.0, 0.0, 0.50, "SALARIED", "LOW", 5_000.0, turnoverRatio
        );
    }

    private TransactionFeatureVector vector(
            AmountFeatures amount,
            VelocityFeatures velocity,
            NoveltyFeatures novelty,
            PeerFeatures peer
    ) {
        return new TransactionFeatureVector(
                "TX-1", "CUSTOMER-1", "ACCOUNT-1",
                LocalDate.of(2026, 8, 5), LocalDateTime.of(2026, 8, 5, novelty.unusualTransactionHour() ? 2 : 12, 0),
                "AML_FEATURES_V2", amount,
                new BehaviorFeatures(1.0, 0.0, 0.0, 1, 1, 1, 60.0),
                new TimeFeatures(novelty.unusualTransactionHour() ? 2 : 12, 3, novelty.unusualTransactionHour(), false, 60.0),
                velocity, novelty,
                new ProfileFeatures(30, 30, 30, 1.0, ProfileStatus.ESTABLISHED),
                peer, "AML_MODEL_FEATURES_V1", Map.of(), Instant.parse("2026-08-05T00:00:00Z")
        );
    }
}
