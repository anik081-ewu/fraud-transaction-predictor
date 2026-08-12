package com.ftd.fraud_transaction_detector.aml.feature.calculator;

import com.ftd.fraud_transaction_detector.aml.feature.domain.AmountFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.FeatureContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.HistoricalTransaction;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public class AmountFeatureCalculator {

    public AmountFeatures calculate(FeatureContext context) {
        double currentAmount = context.currentTransaction().amount().doubleValue();
        Double currentBalance = decimal(context.currentTransaction().balance());
        List<Double> last5 = amounts(context.recentTransactions(), 5);
        List<Double> last30 = amounts(context.recentTransactions(), 30);
        Double last30Average = average(last30);
        Double last30Median = median(last30);
        Double last30Std = standardDeviation(last30, last30Average);

        return new AmountFeatures(
                currentAmount,
                currentBalance,
                safeDivide(currentAmount, currentBalance),
                average(last5),
                median(last5),
                last30Average,
                last30Median,
                last30Std,
                last30.stream().max(Comparator.naturalOrder()).orElse(null),
                last30.stream().min(Comparator.naturalOrder()).orElse(null),
                safeDivide(currentAmount, last30Average),
                safeDivide(currentAmount, last30Median),
                zScore(currentAmount, last30Average, last30Std)
        );
    }

    private static List<Double> amounts(List<HistoricalTransaction> history, int limit) {
        return history.stream().limit(limit).map(item -> item.amount().doubleValue()).toList();
    }

    static Double safeDivide(double numerator, Double denominator) {
        return denominator == null || denominator == 0 ? null : numerator / denominator;
    }

    private static Double average(List<Double> values) {
        return values.isEmpty() ? null : values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    private static Double median(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0
                ? (sorted.get(middle - 1) + sorted.get(middle)) / 2
                : sorted.get(middle);
    }

    private static Double standardDeviation(List<Double> values, Double average) {
        if (values.isEmpty() || average == null) {
            return null;
        }
        double variance = values.stream()
                .mapToDouble(value -> Math.pow(value - average, 2))
                .average()
                .orElse(0);
        return Math.sqrt(variance);
    }

    private static Double zScore(double amount, Double average, Double standardDeviation) {
        return average == null || standardDeviation == null || standardDeviation == 0
                ? null
                : (amount - average) / standardDeviation;
    }

    private static Double decimal(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
