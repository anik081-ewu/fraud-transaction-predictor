package com.ftd.fraud_transaction_detector.aml.feature.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftd.fraud_transaction_detector.aml.feature.application.FeatureVersionProvider;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TransactionFeatureVector;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;

@Repository
public class FeaturePersistenceService {

    private static final String INSERT_SQL = """
            INSERT INTO aml_transaction_features (
                transaction_id, customer_id, account_id, business_date, transaction_date, feature_version,
                current_amount, current_balance, amount_balance_ratio, transaction_hour,
                transaction_day_of_week, is_night, is_weekend,
                customer_history_count, trusted_history_count, recent_transaction_count, profile_confidence,
                last_5_avg_amount, last_5_median_amount, last_30_avg_amount, last_30_median_amount,
                last_30_std_amount, last_30_max_amount, last_30_min_amount,
                amount_vs_last_30_avg, amount_vs_last_30_median, amount_z_score_last_30,
                last_30_debit_ratio, last_30_credit_ratio, last_30_cash_ratio,
                last_30_unique_beneficiaries, last_30_unique_locations, last_30_unique_channels,
                last_30_avg_time_gap_minutes,
                transaction_count_10m, transaction_count_1h, transaction_count_24h,
                transaction_count_7d, transaction_count_30d,
                amount_sum_10m, amount_sum_1h, amount_sum_24h, amount_sum_7d, amount_sum_30d,
                unique_beneficiaries_1h, unique_beneficiaries_24h, unique_beneficiaries_7d,
                repeated_amount_count_24h, below_threshold_count_24h, below_threshold_amount_sum_24h,
                new_beneficiary, new_location, new_channel, new_device, unusual_transaction_hour,
                time_since_previous_transaction_minutes,
                peer_group_code, peer_avg_amount, peer_median_amount, peer_std_amount,
                amount_vs_peer_avg, peer_amount_z_score, peer_frequency_percentile,
                customer_type, customer_risk_rating, expected_monthly_turnover, amount_vs_expected_turnover,
                terminal_tx_count_1d, terminal_fraud_count_1d, terminal_fraud_rate_1d, terminal_avg_amount_1d,
                terminal_tx_count_7d, terminal_fraud_count_7d, terminal_fraud_rate_7d, terminal_avg_amount_7d,
                terminal_tx_count_30d, terminal_fraud_count_30d, terminal_fraud_rate_30d, terminal_avg_amount_30d,
                terminal_risk_available,
                model_feature_schema, model_features_json,
                generated_at, generator_service_version
            ) VALUES (
                :transactionId, :customerId, :accountId, :businessDate, :transactionDate, :featureVersion,
                :currentAmount, :currentBalance, :amountBalanceRatio, :transactionHour,
                :transactionDayOfWeek, :night, :weekend,
                :customerHistoryCount, :trustedHistoryCount, :recentTransactionCount, :profileConfidence,
                :last5Average, :last5Median, :last30Average, :last30Median,
                :last30Std, :last30Maximum, :last30Minimum,
                :amountVsLast30Average, :amountVsLast30Median, :amountZScoreLast30,
                :last30DebitRatio, :last30CreditRatio, :last30CashRatio,
                :last30UniqueBeneficiaries, :last30UniqueLocations, :last30UniqueChannels,
                :last30AverageTimeGapMinutes,
                :transactionCount10Minutes, :transactionCount1Hour, :transactionCount24Hours,
                :transactionCount7Days, :transactionCount30Days,
                :amountSum10Minutes, :amountSum1Hour, :amountSum24Hours, :amountSum7Days, :amountSum30Days,
                :uniqueBeneficiaries1Hour, :uniqueBeneficiaries24Hours, :uniqueBeneficiaries7Days,
                :repeatedAmountCount24Hours, :belowThresholdCount24Hours, :belowThresholdAmountSum24Hours,
                :newBeneficiary, :newLocation, :newChannel, :newDevice, :unusualTransactionHour,
                :minutesSincePrevious,
                :peerGroupCode, :peerAverage, :peerMedian, :peerStd,
                :amountVsPeerAverage, :peerAmountZScore, :peerFrequencyPercentile,
                :customerType, :customerRiskRating, :expectedMonthlyTurnover, :amountVsExpectedTurnover,
                :terminalTxCount1d, :terminalFraudCount1d, :terminalFraudRate1d, :terminalAvgAmount1d,
                :terminalTxCount7d, :terminalFraudCount7d, :terminalFraudRate7d, :terminalAvgAmount7d,
                :terminalTxCount30d, :terminalFraudCount30d, :terminalFraudRate30d, :terminalAvgAmount30d,
                :terminalRiskAvailable,
                :modelFeatureSchema, :modelFeaturesJson,
                :generatedAt, :generatorVersion
            )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final FeatureVersionProvider versionProvider;
    private final ObjectMapper objectMapper;

    public FeaturePersistenceService(
            NamedParameterJdbcTemplate jdbcTemplate,
            FeatureVersionProvider versionProvider,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.versionProvider = versionProvider;
        this.objectMapper = objectMapper;
    }

    public void saveAll(List<TransactionFeatureVector> vectors) {
        if (vectors.isEmpty()) return;
        jdbcTemplate.batchUpdate(INSERT_SQL, vectors.stream()
                .map(this::buildParams)
                .toArray(MapSqlParameterSource[]::new));
    }

    public void save(TransactionFeatureVector vector) {
        jdbcTemplate.update(INSERT_SQL, buildParams(vector));
    }

    private MapSqlParameterSource buildParams(TransactionFeatureVector vector) {
        var amount = vector.amount();
        var behavior = vector.behavior();
        var time = vector.time();
        var velocity = vector.velocity();
        var novelty = vector.novelty();
        var profile = vector.profile();
        var peer = vector.peer();
        var terminalRisk = vector.terminalRisk();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("transactionId", vector.transactionId()).addValue("customerId", vector.customerId())
                .addValue("accountId", vector.accountId()).addValue("businessDate", vector.businessDate())
                .addValue("transactionDate", vector.transactionDate()).addValue("featureVersion", vector.featureVersion())
                .addValue("currentAmount", amount.currentAmount()).addValue("currentBalance", amount.currentBalance())
                .addValue("amountBalanceRatio", amount.amountBalanceRatio()).addValue("transactionHour", time.transactionHour())
                .addValue("transactionDayOfWeek", time.transactionDayOfWeek()).addValue("night", time.night())
                .addValue("weekend", time.weekend()).addValue("customerHistoryCount", profile.customerHistoryCount())
                .addValue("trustedHistoryCount", profile.trustedHistoryCount()).addValue("recentTransactionCount", profile.recentTransactionCount())
                .addValue("profileConfidence", profile.confidence()).addValue("last5Average", amount.last5Average())
                .addValue("last5Median", amount.last5Median()).addValue("last30Average", amount.last30Average())
                .addValue("last30Median", amount.last30Median()).addValue("last30Std", amount.last30StandardDeviation())
                .addValue("last30Maximum", amount.last30Maximum()).addValue("last30Minimum", amount.last30Minimum())
                .addValue("amountVsLast30Average", amount.amountVsLast30Average()).addValue("amountVsLast30Median", amount.amountVsLast30Median())
                .addValue("amountZScoreLast30", amount.amountZScoreLast30()).addValue("last30DebitRatio", behavior.last30DebitRatio())
                .addValue("last30CreditRatio", behavior.last30CreditRatio()).addValue("last30CashRatio", behavior.last30CashRatio())
                .addValue("last30UniqueBeneficiaries", behavior.last30UniqueBeneficiaries()).addValue("last30UniqueLocations", behavior.last30UniqueLocations())
                .addValue("last30UniqueChannels", behavior.last30UniqueChannels()).addValue("last30AverageTimeGapMinutes", behavior.last30AverageTimeGapMinutes())
                .addValue("transactionCount10Minutes", velocity.transactionCount10Minutes()).addValue("transactionCount1Hour", velocity.transactionCount1Hour())
                .addValue("transactionCount24Hours", velocity.transactionCount24Hours()).addValue("transactionCount7Days", velocity.transactionCount7Days())
                .addValue("transactionCount30Days", velocity.transactionCount30Days()).addValue("amountSum10Minutes", velocity.amountSum10Minutes())
                .addValue("amountSum1Hour", velocity.amountSum1Hour()).addValue("amountSum24Hours", velocity.amountSum24Hours())
                .addValue("amountSum7Days", velocity.amountSum7Days()).addValue("amountSum30Days", velocity.amountSum30Days())
                .addValue("uniqueBeneficiaries1Hour", velocity.uniqueBeneficiaries1Hour()).addValue("uniqueBeneficiaries24Hours", velocity.uniqueBeneficiaries24Hours())
                .addValue("uniqueBeneficiaries7Days", velocity.uniqueBeneficiaries7Days()).addValue("repeatedAmountCount24Hours", velocity.repeatedAmountCount24Hours())
                .addValue("belowThresholdCount24Hours", velocity.belowThresholdCount24Hours()).addValue("belowThresholdAmountSum24Hours", velocity.belowThresholdAmountSum24Hours())
                .addValue("newBeneficiary", novelty.newBeneficiary()).addValue("newLocation", novelty.newLocation())
                .addValue("newChannel", novelty.newChannel()).addValue("newDevice", novelty.newDevice())
                .addValue("unusualTransactionHour", novelty.unusualTransactionHour()).addValue("minutesSincePrevious", time.minutesSincePreviousTransaction())
                .addValue("peerGroupCode", peer.peerGroupCode()).addValue("peerAverage", peer.peerAverageAmount())
                .addValue("peerMedian", peer.peerMedianAmount()).addValue("peerStd", peer.peerStandardDeviationAmount())
                .addValue("amountVsPeerAverage", peer.amountVsPeerAverage()).addValue("peerAmountZScore", peer.peerAmountZScore())
                .addValue("peerFrequencyPercentile", peer.peerFrequencyPercentile()).addValue("customerType", peer.customerType())
                .addValue("customerRiskRating", peer.customerRiskRating()).addValue("expectedMonthlyTurnover", peer.expectedMonthlyTurnover())
                .addValue("amountVsExpectedTurnover", peer.amountVsExpectedTurnover())
                .addValue("terminalTxCount1d", terminalRisk.transactionCount1Day())
                .addValue("terminalFraudCount1d", terminalRisk.confirmedFraudCount1Day())
                .addValue("terminalFraudRate1d", terminalRisk.fraudRate1Day())
                .addValue("terminalAvgAmount1d", terminalRisk.averageAmount1Day())
                .addValue("terminalTxCount7d", terminalRisk.transactionCount7Days())
                .addValue("terminalFraudCount7d", terminalRisk.confirmedFraudCount7Days())
                .addValue("terminalFraudRate7d", terminalRisk.fraudRate7Days())
                .addValue("terminalAvgAmount7d", terminalRisk.averageAmount7Days())
                .addValue("terminalTxCount30d", terminalRisk.transactionCount30Days())
                .addValue("terminalFraudCount30d", terminalRisk.confirmedFraudCount30Days())
                .addValue("terminalFraudRate30d", terminalRisk.fraudRate30Days())
                .addValue("terminalAvgAmount30d", terminalRisk.averageAmount30Days())
                .addValue("terminalRiskAvailable", terminalRisk.available())
                .addValue("modelFeatureSchema", vector.modelFeatureSchema()).addValue("modelFeaturesJson", toJson(vector))
                .addValue("generatedAt", Timestamp.from(vector.generatedAt()), Types.TIMESTAMP)
                .addValue("generatorVersion", versionProvider.generatorVersion());
        registerSqlTypes(parameters);
        return parameters;
    }

    private void registerSqlTypes(MapSqlParameterSource parameters) {
        register(parameters, Types.VARCHAR,
                "transactionId", "customerId", "accountId", "featureVersion", "peerGroupCode",
                "customerType", "customerRiskRating", "modelFeatureSchema", "generatorVersion");
        register(parameters, Types.DATE, "businessDate");
        register(parameters, Types.TIMESTAMP, "transactionDate", "generatedAt");
        register(parameters, Types.DECIMAL, "currentAmount", "currentBalance");
        register(parameters, Types.BIGINT, "customerHistoryCount", "trustedHistoryCount");
        register(parameters, Types.INTEGER,
                "transactionHour", "transactionDayOfWeek", "recentTransactionCount",
                "last30UniqueBeneficiaries", "last30UniqueLocations", "last30UniqueChannels",
                "transactionCount10Minutes", "transactionCount1Hour", "transactionCount24Hours",
                "transactionCount7Days", "transactionCount30Days", "uniqueBeneficiaries1Hour",
                "uniqueBeneficiaries24Hours", "uniqueBeneficiaries7Days", "repeatedAmountCount24Hours",
                "belowThresholdCount24Hours", "terminalTxCount1d", "terminalFraudCount1d",
                "terminalTxCount7d", "terminalFraudCount7d", "terminalTxCount30d", "terminalFraudCount30d");
        register(parameters, Types.BIT,
                "night", "weekend", "newBeneficiary", "newLocation", "newChannel", "newDevice",
                "unusualTransactionHour", "terminalRiskAvailable");
        register(parameters, Types.DOUBLE,
                "amountBalanceRatio", "profileConfidence", "last5Average", "last5Median",
                "last30Average", "last30Median", "last30Std", "last30Maximum", "last30Minimum",
                "amountVsLast30Average", "amountVsLast30Median", "amountZScoreLast30",
                "last30DebitRatio", "last30CreditRatio", "last30CashRatio",
                "last30AverageTimeGapMinutes", "amountSum10Minutes", "amountSum1Hour",
                "amountSum24Hours", "amountSum7Days", "amountSum30Days", "belowThresholdAmountSum24Hours",
                "minutesSincePrevious", "peerAverage", "peerMedian", "peerStd", "amountVsPeerAverage",
                "peerAmountZScore", "peerFrequencyPercentile", "expectedMonthlyTurnover",
                "amountVsExpectedTurnover", "terminalFraudRate1d", "terminalAvgAmount1d",
                "terminalFraudRate7d", "terminalAvgAmount7d", "terminalFraudRate30d", "terminalAvgAmount30d");
        register(parameters, Types.NVARCHAR, "modelFeaturesJson");
    }

    private void register(MapSqlParameterSource parameters, int sqlType, String... names) {
        for (String name : names) {
            parameters.registerSqlType(name, sqlType);
        }
    }

    private String toJson(TransactionFeatureVector vector) {
        try {
            return objectMapper.writeValueAsString(vector.modelFeatures());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize persisted model features", exception);
        }
    }
}
