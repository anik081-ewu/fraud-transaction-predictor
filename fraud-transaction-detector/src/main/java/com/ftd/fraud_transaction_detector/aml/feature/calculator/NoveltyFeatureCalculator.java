package com.ftd.fraud_transaction_detector.aml.feature.calculator;

import com.ftd.fraud_transaction_detector.aml.feature.domain.FeatureContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.HistoricalTransaction;
import com.ftd.fraud_transaction_detector.aml.feature.domain.NoveltyFeatures;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public class NoveltyFeatureCalculator {

    public NoveltyFeatures calculate(FeatureContext context) {
        List<HistoricalTransaction> trustedHistory = context.trustedTransactions();
        var current = context.currentTransaction();
        return new NoveltyFeatures(
                unseen(current.beneficiaryId(), trustedHistory, HistoricalTransaction::beneficiaryId),
                unseen(current.location(), trustedHistory, HistoricalTransaction::location),
                unseen(current.channel(), trustedHistory, HistoricalTransaction::channel),
                unseen(current.deviceId(), trustedHistory, HistoricalTransaction::deviceId),
                unusualHour(current.transactionDate().getHour(), context)
        );
    }

    private static boolean unseen(
            String currentValue,
            List<HistoricalTransaction> history,
            Function<HistoricalTransaction, String> extractor
    ) {
        String normalizedCurrent = normalize(currentValue);
        if (normalizedCurrent == null) {
            return false;
        }
        return history.stream()
                .map(extractor)
                .map(NoveltyFeatureCalculator::normalize)
                .noneMatch(normalizedCurrent::equals);
    }

    private static boolean unusualHour(int hour, FeatureContext context) {
        Integer start = context.trustedProfile().usualStartHour();
        Integer end = context.trustedProfile().usualEndHour();
        if (start == null || end == null) {
            return false;
        }
        boolean usual = start <= end
                ? hour >= start && hour <= end
                : hour >= start || hour <= end;
        return !usual;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
