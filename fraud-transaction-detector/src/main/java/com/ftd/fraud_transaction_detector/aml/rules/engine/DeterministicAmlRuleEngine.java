package com.ftd.fraud_transaction_detector.aml.rules.engine;

import com.ftd.fraud_transaction_detector.aml.feature.domain.TransactionFeatureVector;
import com.ftd.fraud_transaction_detector.aml.risk.infrastructure.AppConfigRiskPolicyRepository;
import com.ftd.fraud_transaction_detector.aml.rules.domain.AmlRuleEngine;
import com.ftd.fraud_transaction_detector.aml.rules.domain.RuleEngineResult;
import com.ftd.fraud_transaction_detector.aml.rules.domain.RuleEvaluationContext;
import com.ftd.fraud_transaction_detector.aml.rules.domain.RuleSeverity;
import com.ftd.fraud_transaction_detector.aml.rules.domain.TriggeredRule;
import com.ftd.fraud_transaction_detector.aml.scoring.domain.NormalizedScore;
import com.ftd.fraud_transaction_detector.config.repo.AppConfigRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DeterministicAmlRuleEngine implements AmlRuleEngine {

    public static final String REPORTING_THRESHOLD_ATTRIBUTE = "reportingThreshold";
    public static final String SANCTIONS_MATCH_ATTRIBUTE = "sanctionsMatch";

    private final AppConfigRepository appConfigRepository;
    private final DeterministicAmlRulePolicy fixedPolicy;

    @Autowired
    public DeterministicAmlRuleEngine(AppConfigRepository appConfigRepository) {
        this.appConfigRepository = appConfigRepository;
        this.fixedPolicy = null;
    }

    DeterministicAmlRuleEngine(DeterministicAmlRulePolicy policy) {
        this.appConfigRepository = null;
        this.fixedPolicy = policy;
    }

    private DeterministicAmlRulePolicy policy() {
        if (fixedPolicy != null) return fixedPolicy;
        DeterministicAmlRulePolicy defaults = DeterministicAmlRulePolicy.transparentV1();
        try {
            return new DeterministicAmlRulePolicy(
                    defaults.normalizationVersion(),
                    optionalDouble(AppConfigRiskPolicyRepository.RULES_REPORTING_THRESHOLD, defaults.defaultReportingThreshold()),
                    optionalInt(AppConfigRiskPolicyRepository.RULES_STRUCTURING_COUNT_24H, defaults.structuringMinimumCount24Hours()),
                    optionalInt(AppConfigRiskPolicyRepository.RULES_RAPID_TX_COUNT_10M, defaults.rapidTransactionCount10Minutes()),
                    optionalInt(AppConfigRiskPolicyRepository.RULES_HIGH_TX_COUNT_1H, defaults.highTransactionCount1Hour()),
                    optionalInt(AppConfigRiskPolicyRepository.RULES_MULTI_BENEFICIARY_COUNT_1H, defaults.multipleBeneficiaryCount1Hour()),
                    optionalInt(AppConfigRiskPolicyRepository.RULES_REPEATED_AMOUNT_COUNT_24H, defaults.repeatedAmountCount24Hours()),
                    optionalDouble(AppConfigRiskPolicyRepository.RULES_HIGH_CUSTOMER_AMOUNT_RATIO, defaults.highCustomerAmountRatio()),
                    optionalDouble(AppConfigRiskPolicyRepository.RULES_EXTREME_CUSTOMER_AMOUNT_RATIO, defaults.extremeCustomerAmountRatio()),
                    optionalDouble(AppConfigRiskPolicyRepository.RULES_HIGH_BALANCE_RATIO, defaults.highBalanceRatio()),
                    optionalDouble(AppConfigRiskPolicyRepository.RULES_HIGH_EXPECTED_TURNOVER_RATIO, defaults.highExpectedTurnoverRatio())
            );
        } catch (IllegalArgumentException ignored) {
            return defaults;
        }
    }

    private double optionalDouble(String key, double defaultValue) {
        try {
            return appConfigRepository.findById(key)
                    .map(c -> c.getConfigValue()).map(String::trim).filter(v -> !v.isBlank())
                    .map(Double::parseDouble).filter(Double::isFinite).orElse(defaultValue);
        } catch (NumberFormatException ignored) { return defaultValue; }
    }

    private int optionalInt(String key, int defaultValue) {
        try {
            return appConfigRepository.findById(key)
                    .map(c -> c.getConfigValue()).map(String::trim).filter(v -> !v.isBlank())
                    .map(Integer::parseInt).orElse(defaultValue);
        } catch (NumberFormatException ignored) { return defaultValue; }
    }

    @Override
    public RuleEngineResult evaluate(
            TransactionFeatureVector features,
            RuleEvaluationContext context
    ) {
        if (features == null) throw new IllegalArgumentException("features are required");
        if (context == null) throw new IllegalArgumentException("context is required");

        DeterministicAmlRulePolicy policy = policy();
        List<TriggeredRule> triggered = new ArrayList<>();
        evaluateSanctions(context, triggered);
        evaluateStructuring(features, context, policy, triggered);
        evaluateVelocity(features, policy, triggered);
        evaluateAmount(features, policy, triggered);
        evaluateNovelty(features, triggered);
        evaluateTurnover(features, policy, triggered);

        List<TriggeredRule> ordered = triggered.stream()
                .sorted(Comparator.comparing(TriggeredRule::ruleCode))
                .toList();
        double normalized = cumulativeScore(ordered);
        RuleSeverity highestSeverity = ordered.stream()
                .map(TriggeredRule::severity)
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(RuleSeverity.NONE);
        boolean hardAlert = ordered.stream().anyMatch(TriggeredRule::hardOverride);

        return new RuleEngineResult(
                new NormalizedScore(normalized * 100.0, normalized, policy.normalizationVersion()),
                highestSeverity,
                hardAlert,
                ordered,
                ordered.stream().map(TriggeredRule::ruleCode).toList()
        );
    }

    private void evaluateSanctions(RuleEvaluationContext context, List<TriggeredRule> triggered) {
        if (booleanAttribute(context.attributes(), SANCTIONS_MATCH_ATTRIBUTE)) {
            triggered.add(rule(
                    "SANCTIONS_MATCH", RuleSeverity.CRITICAL, 1.0, true,
                    "screeningResult", true
            ));
        }
    }

    private void evaluateStructuring(
            TransactionFeatureVector features,
            RuleEvaluationContext context,
            DeterministicAmlRulePolicy policy,
            List<TriggeredRule> triggered
    ) {
        double reportingThreshold = positiveNumberAttribute(
                context.attributes(), REPORTING_THRESHOLD_ATTRIBUTE, policy.defaultReportingThreshold()
        );
        int count = features.velocity().belowThresholdCount24Hours();
        double sum = features.velocity().belowThresholdAmountSum24Hours();
        if (count >= policy.structuringMinimumCount24Hours() && sum >= reportingThreshold) {
            triggered.add(rule(
                    "POTENTIAL_STRUCTURING_24H", RuleSeverity.HIGH, 0.90, false,
                    Map.of(
                            "belowThresholdCount24Hours", count,
                            "belowThresholdAmountSum24Hours", sum,
                            "reportingThreshold", reportingThreshold
                    )
            ));
        }
    }

    private void evaluateVelocity(TransactionFeatureVector features, DeterministicAmlRulePolicy policy, List<TriggeredRule> triggered) {
        var velocity = features.velocity();
        if (velocity.transactionCount10Minutes() >= policy.rapidTransactionCount10Minutes()) {
            triggered.add(rule(
                    "RAPID_TRANSACTION_VELOCITY_10M", RuleSeverity.HIGH, 0.75, false,
                    "transactionCount10Minutes", velocity.transactionCount10Minutes()
            ));
        }
        if (velocity.transactionCount1Hour() >= policy.highTransactionCount1Hour()) {
            triggered.add(rule(
                    "HIGH_TRANSACTION_VELOCITY_1H", RuleSeverity.HIGH, 0.80, false,
                    "transactionCount1Hour", velocity.transactionCount1Hour()
            ));
        }
        if (velocity.uniqueBeneficiaries1Hour() >= policy.multipleBeneficiaryCount1Hour()) {
            triggered.add(rule(
                    "MULTIPLE_BENEFICIARIES_1H", RuleSeverity.MEDIUM, 0.65, false,
                    "uniqueBeneficiaries1Hour", velocity.uniqueBeneficiaries1Hour()
            ));
        }
        if (velocity.repeatedAmountCount24Hours() >= policy.repeatedAmountCount24Hours()) {
            triggered.add(rule(
                    "REPEATED_AMOUNT_PATTERN_24H", RuleSeverity.MEDIUM, 0.60, false,
                    "repeatedAmountCount24Hours", velocity.repeatedAmountCount24Hours()
            ));
        }
    }

    private void evaluateAmount(TransactionFeatureVector features, DeterministicAmlRulePolicy policy, List<TriggeredRule> triggered) {
        Double customerRatio = features.amount().amountVsLast30Average();
        if (isFinite(customerRatio) && customerRatio >= policy.extremeCustomerAmountRatio()) {
            triggered.add(rule(
                    "EXTREME_AMOUNT_VS_CUSTOMER_AVERAGE", RuleSeverity.HIGH, 0.90, false,
                    "amountVsLast30Average", customerRatio
            ));
        } else if (isFinite(customerRatio) && customerRatio >= policy.highCustomerAmountRatio()) {
            triggered.add(rule(
                    "HIGH_AMOUNT_VS_CUSTOMER_AVERAGE", RuleSeverity.HIGH, 0.75, false,
                    "amountVsLast30Average", customerRatio
            ));
        }

        Double balanceRatio = features.amount().amountBalanceRatio();
        if (isFinite(balanceRatio) && balanceRatio >= policy.highBalanceRatio()) {
            triggered.add(rule(
                    "HIGH_TRANSACTION_TO_BALANCE_RATIO", RuleSeverity.HIGH, 0.85, false,
                    "amountBalanceRatio", balanceRatio
            ));
        }
    }

    private void evaluateNovelty(TransactionFeatureVector features, List<TriggeredRule> triggered) {
        var novelty = features.novelty();
        if (novelty.newBeneficiary() && novelty.newDevice() && novelty.unusualTransactionHour()) {
            triggered.add(rule(
                    "NEW_BENEFICIARY_DEVICE_AT_UNUSUAL_HOUR", RuleSeverity.HIGH, 0.85, false,
                    Map.of("newBeneficiary", true, "newDevice", true, "unusualTransactionHour", true)
            ));
        } else if (novelty.newLocation() && novelty.newDevice()) {
            triggered.add(rule(
                    "NEW_LOCATION_AND_DEVICE", RuleSeverity.MEDIUM, 0.65, false,
                    Map.of("newLocation", true, "newDevice", true)
            ));
        } else if (novelty.unusualTransactionHour()) {
            triggered.add(rule(
                    "UNUSUAL_TRANSACTION_HOUR", RuleSeverity.LOW, 0.25, false,
                    "transactionHour", features.time().transactionHour()
            ));
        }
    }

    private void evaluateTurnover(TransactionFeatureVector features, DeterministicAmlRulePolicy policy, List<TriggeredRule> triggered) {
        Double turnoverRatio = features.peer().amountVsExpectedTurnover();
        if (isFinite(turnoverRatio) && turnoverRatio >= policy.highExpectedTurnoverRatio()) {
            triggered.add(rule(
                    "HIGH_TRANSACTION_TO_EXPECTED_TURNOVER", RuleSeverity.HIGH, 0.80, false,
                    "amountVsExpectedTurnover", turnoverRatio
            ));
        }
    }

    private static double cumulativeScore(List<TriggeredRule> rules) {
        double remainingNormalEvidence = 1.0;
        for (TriggeredRule rule : rules) remainingNormalEvidence *= 1.0 - rule.score();
        return clamp(1.0 - remainingNormalEvidence);
    }

    private static TriggeredRule rule(
            String code,
            RuleSeverity severity,
            double score,
            boolean hardOverride,
            String evidenceKey,
            Object evidenceValue
    ) {
        return rule(code, severity, score, hardOverride, Map.of(evidenceKey, evidenceValue));
    }

    private static TriggeredRule rule(
            String code,
            RuleSeverity severity,
            double score,
            boolean hardOverride,
            Map<String, Object> evidence
    ) {
        return new TriggeredRule(code, severity, score, hardOverride, evidence);
    }

    private static boolean booleanAttribute(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        return value instanceof Boolean booleanValue && booleanValue;
    }

    private static double positiveNumberAttribute(
            Map<String, Object> attributes,
            String key,
            double defaultValue
    ) {
        Object value = attributes.get(key);
        if (value instanceof Number number && Double.isFinite(number.doubleValue()) && number.doubleValue() > 0.0) {
            return number.doubleValue();
        }
        return defaultValue;
    }

    private static boolean isFinite(Double value) {
        return value != null && Double.isFinite(value);
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
