package com.ftd.fraud_transaction_detector.aml.feature.calculator;

import com.ftd.fraud_transaction_detector.aml.feature.domain.FeatureContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.HistoricalTransaction;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class LegacyModelFeatureCalculator {

    public static final String SCHEMA = "LEGACY_MODEL_INPUT_V1";
    private static final int LOCATION_HASH_BUCKETS = 128;

    public Map<String, Double> calculate(FeatureContext context) {
        var current = context.currentTransaction();
        var trusted = context.trustedProfile();
        HistoricalTransaction previous = context.recentTransactions().stream().findFirst().orElse(null);
        double amount = current.amount().doubleValue();
        double balance = current.balance() == null ? 0 : current.balance().doubleValue();
        double average = valueOrZero(trusted.averageAmount());
        double maximum = valueOrZero(trusted.maximumAmount());
        double standardDeviation = valueOrZero(trusted.standardDeviationAmount());
        double rolling7 = rollingAverage(context, 7);
        double rolling30 = rollingAverage(context, 30);
        int loginAttempts = Math.max(0, current.loginAttempts());

        Map<String, Double> features = new LinkedHashMap<>();
        features.put("time_diff_hours", previous == null ? 0.0 :
                Duration.between(previous.transactionDate(), current.transactionDate()).toMinutes() / 60.0);
        features.put("amount_balance_ratio", safeDivide(amount, balance));
        features.put("transaction_hour", (double) current.transactionDate().getHour());
        features.put("is_night", current.transactionDate().getHour() <= 5 ? 1.0 : 0.0);
        features.put("high_login_attempts", loginAttempts >= 3 ? 1.0 : 0.0);
        features.put("user_avg_amount", average);
        features.put("user_max_amount", maximum);
        features.put("user_txn_count", (double) trusted.transactionCount());
        features.put("amount_vs_user_avg", safeDivide(amount, average));
        features.put("amount_vs_user_max", safeDivide(amount, maximum));
        features.put("user_amount_std", standardDeviation);
        features.put("amount_z_score_user", standardDeviation == 0 ? 0 : (amount - average) / standardDeviation);
        features.put("transaction_dayofweek", (double) current.transactionDate().getDayOfWeek().getValue() - 1);
        features.put("is_weekend", current.transactionDate().getDayOfWeek().getValue() >= 6 ? 1.0 : 0.0);
        features.put("login_attempt_risk", Math.min(loginAttempts / 10.0, 1.0));
        features.put("rolling_7d_avg_amount", rolling7);
        features.put("rolling_30d_avg_amount", rolling30);
        features.put("amount_vs_rolling_7d_avg", safeDivide(amount, rolling7));
        features.put("amount_vs_rolling_30d_avg", safeDivide(amount, rolling30));
        features.put("location_changed", locationChanged(previous, current.location()) ? 1.0 : 0.0);
        oneHot(features, "TransactionType", current.transactionType());
        hashedLocation(features, current.location());
        oneHot(features, "Channel", current.channel());
        oneHot(features, "CustomerOccupation", current.customerOccupation());
        return Map.copyOf(features);
    }

    private double rollingAverage(FeatureContext context, int days) {
        var cutoff = context.currentTransaction().transactionDate().minusDays(days);
        return context.trustedTransactions().stream()
                .filter(transaction -> !transaction.transactionDate().isBefore(cutoff))
                .map(HistoricalTransaction::amount)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0);
    }

    private boolean locationChanged(HistoricalTransaction previous, String currentLocation) {
        return previous != null
                && previous.location() != null
                && !previous.location().isBlank()
                && !previous.location().equalsIgnoreCase(currentLocation == null ? "" : currentLocation);
    }

    private void oneHot(Map<String, Double> features, String prefix, String value) {
        features.put(prefix + "_" + (value == null ? "" : value), 1.0);
    }

    private void hashedLocation(Map<String, Double> features, String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            long prefix = 0;
            for (int index = 0; index < Long.BYTES; index++) {
                prefix = (prefix << 8) | Byte.toUnsignedLong(digest[index]);
            }
            int bucket = (int) Long.remainderUnsigned(prefix, LOCATION_HASH_BUCKETS);
            features.put("LocationHashBucket_%03d".formatted(bucket), 1.0);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private double safeDivide(double numerator, double denominator) {
        return denominator == 0 ? 0 : numerator / denominator;
    }

    private double valueOrZero(Double value) {
        return value == null ? 0 : value;
    }
}
