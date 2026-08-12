package com.ftd.fraud_transaction_detector.aml.risk.infrastructure;

import com.ftd.fraud_transaction_detector.aml.risk.domain.RiskPolicy;
import com.ftd.fraud_transaction_detector.aml.risk.domain.RiskPolicyRepository;
import com.ftd.fraud_transaction_detector.config.repo.AppConfigRepository;
import org.springframework.stereotype.Repository;

@Repository
public class AppConfigRiskPolicyRepository implements RiskPolicyRepository {

    public static final String VERSION = "aml.risk.policy.version";
    public static final String CUSTOMER_WEIGHT = "aml.risk.weight.customer_behaviour";
    public static final String PEER_WEIGHT = "aml.risk.weight.peer_behaviour";
    public static final String ML_ENSEMBLE_WEIGHT = "aml.risk.weight.ml_ensemble";
    public static final String RULES_WEIGHT = "aml.risk.weight.rules";
    public static final String LOW_THRESHOLD = "aml.risk.threshold.low";
    public static final String MEDIUM_THRESHOLD = "aml.risk.threshold.medium";
    public static final String HIGH_THRESHOLD = "aml.risk.threshold.high";
    public static final String CB_AMOUNT_WEIGHT = "aml.risk.weight.customer_behaviour.amount";
    public static final String CB_NOVELTY_WEIGHT = "aml.risk.weight.customer_behaviour.novelty";
    public static final String CB_FREQUENCY_WEIGHT = "aml.risk.weight.customer_behaviour.frequency";
    public static final String CB_TIME_GAP_WEIGHT = "aml.risk.weight.customer_behaviour.time_gap";
    public static final String CB_UNUSUAL_HOUR_WEIGHT = "aml.risk.weight.customer_behaviour.unusual_hour";
    public static final String PB_AMOUNT_WEIGHT = "aml.risk.weight.peer_behaviour.amount";
    public static final String PB_FREQUENCY_WEIGHT = "aml.risk.weight.peer_behaviour.frequency";
    public static final String PB_EXPECTED_TURNOVER_WEIGHT = "aml.risk.weight.peer_behaviour.expected_turnover";
    public static final String RULES_REPORTING_THRESHOLD = "aml.risk.rules.reporting_threshold";
    public static final String RULES_STRUCTURING_COUNT_24H = "aml.risk.rules.structuring_count_24h";
    public static final String RULES_RAPID_TX_COUNT_10M = "aml.risk.rules.rapid_tx_count_10m";
    public static final String RULES_HIGH_TX_COUNT_1H = "aml.risk.rules.high_tx_count_1h";
    public static final String RULES_MULTI_BENEFICIARY_COUNT_1H = "aml.risk.rules.multi_beneficiary_count_1h";
    public static final String RULES_REPEATED_AMOUNT_COUNT_24H = "aml.risk.rules.repeated_amount_count_24h";
    public static final String RULES_HIGH_CUSTOMER_AMOUNT_RATIO = "aml.risk.rules.high_customer_amount_ratio";
    public static final String RULES_EXTREME_CUSTOMER_AMOUNT_RATIO = "aml.risk.rules.extreme_customer_amount_ratio";
    public static final String RULES_HIGH_BALANCE_RATIO = "aml.risk.rules.high_balance_ratio";
    public static final String RULES_HIGH_EXPECTED_TURNOVER_RATIO = "aml.risk.rules.high_expected_turnover_ratio";

    private final AppConfigRepository configRepository;

    public AppConfigRiskPolicyRepository(AppConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @Override
    public RiskPolicy findActive(String customerSegment) {
        return new RiskPolicy(
                required(VERSION),
                requiredDouble(CUSTOMER_WEIGHT),
                requiredDouble(PEER_WEIGHT),
                requiredDouble(ML_ENSEMBLE_WEIGHT),
                requiredDouble(RULES_WEIGHT),
                requiredDouble(LOW_THRESHOLD),
                requiredDouble(MEDIUM_THRESHOLD),
                requiredDouble(HIGH_THRESHOLD)
        );
    }

    private String required(String key) {
        return configRepository.findById(key)
                .map(config -> config.getConfigValue())
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalStateException("Required risk policy config is missing: " + key));
    }

    private double requiredDouble(String key) {
        String raw = required(key);
        try {
            double value = Double.parseDouble(raw);
            if (!Double.isFinite(value)) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Risk policy config is not a finite decimal: " + key, exception);
        }
    }
}
