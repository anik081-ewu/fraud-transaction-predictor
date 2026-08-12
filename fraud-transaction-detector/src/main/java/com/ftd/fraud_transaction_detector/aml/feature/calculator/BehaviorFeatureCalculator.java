package com.ftd.fraud_transaction_detector.aml.feature.calculator;

import com.ftd.fraud_transaction_detector.aml.feature.domain.BehaviorFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.FeatureContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.HistoricalTransaction;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

public class BehaviorFeatureCalculator {

    public BehaviorFeatures calculate(FeatureContext context) {
        List<HistoricalTransaction> last30 = context.recentTransactions().stream().limit(30).toList();
        if (last30.isEmpty()) {
            return new BehaviorFeatures(null, null, null, 0, 0, 0, null);
        }
        double count = last30.size();
        long debits = last30.stream().filter(item -> equalsType(item.transactionType(), "DEBIT")).count();
        long credits = last30.stream().filter(item -> equalsType(item.transactionType(), "CREDIT")).count();
        long cash = last30.stream().filter(item -> containsType(item.transactionType(), "CASH")).count();

        return new BehaviorFeatures(
                debits / count,
                credits / count,
                cash / count,
                distinctCount(last30, HistoricalTransaction::beneficiaryId),
                distinctCount(last30, HistoricalTransaction::location),
                distinctCount(last30, HistoricalTransaction::channel),
                averageGapMinutes(last30)
        );
    }

    private static int distinctCount(
            List<HistoricalTransaction> transactions,
            Function<HistoricalTransaction, String> extractor
    ) {
        return (int) transactions.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .distinct()
                .count();
    }

    private static Double averageGapMinutes(List<HistoricalTransaction> newestFirst) {
        if (newestFirst.size() < 2) {
            return null;
        }
        List<HistoricalTransaction> chronological = new ArrayList<>(newestFirst);
        Collections.reverse(chronological);
        double totalMinutes = 0;
        for (int index = 1; index < chronological.size(); index++) {
            totalMinutes += Duration.between(
                    chronological.get(index - 1).transactionDate(),
                    chronological.get(index).transactionDate()
            ).toMillis() / 60000.0;
        }
        return totalMinutes / (chronological.size() - 1);
    }

    private static boolean equalsType(String value, String expected) {
        return value != null && value.trim().equalsIgnoreCase(expected);
    }

    private static boolean containsType(String value, String expected) {
        return value != null && value.toUpperCase(Locale.ROOT).contains(expected);
    }
}
