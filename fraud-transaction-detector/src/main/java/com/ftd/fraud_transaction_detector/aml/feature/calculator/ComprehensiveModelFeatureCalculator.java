package com.ftd.fraud_transaction_detector.aml.feature.calculator;

import com.ftd.fraud_transaction_detector.aml.feature.domain.AmountFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.BehaviorFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.NoveltyFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.PeerFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.ProfileFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TimeFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.VelocityFeatures;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class ComprehensiveModelFeatureCalculator {

    public static final String SCHEMA = "AML_MODEL_INPUT_V2";

    public Map<String, Double> calculate(
            Map<String, Double> legacy,
            AmountFeatures amount,
            BehaviorFeatures behavior,
            TimeFeatures time,
            VelocityFeatures velocity,
            NoveltyFeatures novelty,
            ProfileFeatures profile,
            PeerFeatures peer
    ) {
        Map<String, Double> features = new LinkedHashMap<>(legacy);
        put(features, "current_amount", amount.currentAmount());
        put(features, "current_balance", amount.currentBalance());
        put(features, "amount_balance_ratio_v2", amount.amountBalanceRatio());
        put(features, "last_5_avg_amount", amount.last5Average());
        put(features, "last_5_median_amount", amount.last5Median());
        put(features, "last_30_avg_amount", amount.last30Average());
        put(features, "last_30_median_amount", amount.last30Median());
        put(features, "last_30_std_amount", amount.last30StandardDeviation());
        put(features, "last_30_max_amount", amount.last30Maximum());
        put(features, "last_30_min_amount", amount.last30Minimum());
        put(features, "amount_vs_last_30_avg", amount.amountVsLast30Average());
        put(features, "amount_vs_last_30_median", amount.amountVsLast30Median());
        put(features, "amount_z_score_last_30", amount.amountZScoreLast30());

        put(features, "last_30_debit_ratio", behavior.last30DebitRatio());
        put(features, "last_30_credit_ratio", behavior.last30CreditRatio());
        put(features, "last_30_cash_ratio", behavior.last30CashRatio());
        put(features, "last_30_unique_beneficiaries", behavior.last30UniqueBeneficiaries());
        put(features, "last_30_unique_locations", behavior.last30UniqueLocations());
        put(features, "last_30_unique_channels", behavior.last30UniqueChannels());
        put(features, "last_30_avg_time_gap_minutes", behavior.last30AverageTimeGapMinutes());

        put(features, "transaction_hour_v2", time.transactionHour());
        put(features, "transaction_day_of_week_v2", time.transactionDayOfWeek());
        put(features, "is_night_v2", time.night());
        put(features, "is_weekend_v2", time.weekend());
        put(features, "time_since_previous_transaction_minutes", time.minutesSincePreviousTransaction());

        put(features, "transaction_count_10m", velocity.transactionCount10Minutes());
        put(features, "transaction_count_1h", velocity.transactionCount1Hour());
        put(features, "transaction_count_24h", velocity.transactionCount24Hours());
        put(features, "transaction_count_7d", velocity.transactionCount7Days());
        put(features, "transaction_count_30d", velocity.transactionCount30Days());
        put(features, "amount_sum_10m", velocity.amountSum10Minutes());
        put(features, "amount_sum_1h", velocity.amountSum1Hour());
        put(features, "amount_sum_24h", velocity.amountSum24Hours());
        put(features, "amount_sum_7d", velocity.amountSum7Days());
        put(features, "amount_sum_30d", velocity.amountSum30Days());
        put(features, "unique_beneficiaries_1h", velocity.uniqueBeneficiaries1Hour());
        put(features, "unique_beneficiaries_24h", velocity.uniqueBeneficiaries24Hours());
        put(features, "unique_beneficiaries_7d", velocity.uniqueBeneficiaries7Days());
        put(features, "repeated_amount_count_24h", velocity.repeatedAmountCount24Hours());
        put(features, "below_threshold_count_24h", velocity.belowThresholdCount24Hours());
        put(features, "below_threshold_amount_sum_24h", velocity.belowThresholdAmountSum24Hours());

        put(features, "new_beneficiary", novelty.newBeneficiary());
        put(features, "new_location", novelty.newLocation());
        put(features, "new_channel", novelty.newChannel());
        put(features, "new_device", novelty.newDevice());
        put(features, "unusual_transaction_hour", novelty.unusualTransactionHour());

        put(features, "customer_history_count", profile.customerHistoryCount());
        put(features, "trusted_history_count", profile.trustedHistoryCount());
        put(features, "recent_transaction_count", profile.recentTransactionCount());
        put(features, "profile_confidence", profile.confidence());
        oneHot(features, "profile_status", profile.status() == null ? null : profile.status().name());

        put(features, "peer_avg_amount", peer.peerAverageAmount());
        put(features, "peer_median_amount", peer.peerMedianAmount());
        put(features, "peer_std_amount", peer.peerStandardDeviationAmount());
        put(features, "amount_vs_peer_avg", peer.amountVsPeerAverage());
        put(features, "peer_amount_z_score", peer.peerAmountZScore());
        put(features, "peer_frequency_percentile", peer.peerFrequencyPercentile());
        put(features, "expected_monthly_turnover", peer.expectedMonthlyTurnover());
        put(features, "amount_vs_expected_turnover", peer.amountVsExpectedTurnover());
        put(features, "peer_baseline_available", peer.peerAverageAmount() != null);
        oneHot(features, "peer_group", peer.peerGroupCode());
        oneHot(features, "customer_type", peer.customerType());
        oneHot(features, "customer_risk_rating", peer.customerRiskRating());
        return Map.copyOf(features);
    }

    private void put(Map<String, Double> features, String key, Number value) {
        features.put(key, value == null ? 0.0 : value.doubleValue());
        if (value == null) features.put(key + "_missing", 1.0);
    }

    private void put(Map<String, Double> features, String key, boolean value) {
        features.put(key, value ? 1.0 : 0.0);
    }

    private void oneHot(Map<String, Double> features, String prefix, String value) {
        String normalized = value == null || value.isBlank()
                ? "UNKNOWN"
                : value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        features.put(prefix + "_" + normalized, 1.0);
    }
}
