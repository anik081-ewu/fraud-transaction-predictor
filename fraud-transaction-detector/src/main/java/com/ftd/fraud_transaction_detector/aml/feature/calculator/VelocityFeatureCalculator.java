package com.ftd.fraud_transaction_detector.aml.feature.calculator;

import com.ftd.fraud_transaction_detector.aml.feature.domain.FeatureContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.HistoricalTransaction;
import com.ftd.fraud_transaction_detector.aml.feature.domain.VelocityFeatures;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

public class VelocityFeatureCalculator {

    public VelocityFeatures calculate(FeatureContext context, BigDecimal reportingThreshold) {
        List<HistoricalTransaction> tenMinutes = within(context, Duration.ofMinutes(10));
        List<HistoricalTransaction> oneHour = within(context, Duration.ofHours(1));
        List<HistoricalTransaction> oneDay = within(context, Duration.ofDays(1));
        List<HistoricalTransaction> sevenDays = within(context, Duration.ofDays(7));
        List<HistoricalTransaction> thirtyDays = within(context, Duration.ofDays(30));
        double currentAmount = context.currentTransaction().amount().doubleValue();
        double threshold = reportingThreshold == null ? 0 : reportingThreshold.doubleValue();

        return new VelocityFeatures(
                tenMinutes.size() + 1,
                oneHour.size() + 1,
                oneDay.size() + 1,
                sevenDays.size() + 1,
                thirtyDays.size() + 1,
                sum(tenMinutes) + currentAmount,
                sum(oneHour) + currentAmount,
                sum(oneDay) + currentAmount,
                sum(sevenDays) + currentAmount,
                sum(thirtyDays) + currentAmount,
                uniqueBeneficiaries(oneHour, context.currentTransaction().beneficiaryId()),
                uniqueBeneficiaries(oneDay, context.currentTransaction().beneficiaryId()),
                uniqueBeneficiaries(sevenDays, context.currentTransaction().beneficiaryId()),
                (int) oneDay.stream().filter(item -> item.amount().compareTo(context.currentTransaction().amount()) == 0).count() + 1,
                belowThresholdCount(oneDay, currentAmount, threshold),
                belowThresholdSum(oneDay, currentAmount, threshold)
        );
    }

    private static List<HistoricalTransaction> within(FeatureContext context, Duration duration) {
        var lowerBound = context.currentTransaction().transactionDate().minus(duration);
        return context.recentTransactions().stream()
                .filter(item -> !item.transactionDate().isBefore(lowerBound))
                .toList();
    }

    private static double sum(List<HistoricalTransaction> transactions) {
        return transactions.stream().mapToDouble(item -> item.amount().doubleValue()).sum();
    }

    private static int uniqueBeneficiaries(List<HistoricalTransaction> transactions, String currentBeneficiary) {
        return (int) Stream.concat(
                        transactions.stream().map(HistoricalTransaction::beneficiaryId),
                        Stream.of(currentBeneficiary)
                )
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .distinct()
                .count();
    }

    private static int belowThresholdCount(
            List<HistoricalTransaction> transactions,
            double currentAmount,
            double threshold
    ) {
        int priorCount = (int) transactions.stream()
                .filter(item -> item.amount().doubleValue() < threshold)
                .count();
        return priorCount + (currentAmount < threshold ? 1 : 0);
    }

    private static double belowThresholdSum(
            List<HistoricalTransaction> transactions,
            double currentAmount,
            double threshold
    ) {
        double priorSum = transactions.stream()
                .mapToDouble(item -> item.amount().doubleValue())
                .filter(amount -> amount < threshold)
                .sum();
        return priorSum + (currentAmount < threshold ? currentAmount : 0);
    }
}
