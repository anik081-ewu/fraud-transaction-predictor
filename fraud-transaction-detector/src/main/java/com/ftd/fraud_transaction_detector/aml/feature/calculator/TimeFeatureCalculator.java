package com.ftd.fraud_transaction_detector.aml.feature.calculator;

import com.ftd.fraud_transaction_detector.aml.feature.domain.FeatureContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TimeFeatures;

import java.time.Duration;

public class TimeFeatureCalculator {

    public TimeFeatures calculate(FeatureContext context) {
        var transactionDate = context.currentTransaction().transactionDate();
        int hour = transactionDate.getHour();
        int dayOfWeek = transactionDate.getDayOfWeek().getValue();
        Double minutesSincePrevious = context.recentTransactions().isEmpty()
                ? null
                : Duration.between(
                        context.recentTransactions().get(0).transactionDate(),
                        transactionDate
                ).toMillis() / 60000.0;
        return new TimeFeatures(
                hour,
                dayOfWeek,
                hour <= 5,
                dayOfWeek >= 6,
                minutesSincePrevious
        );
    }
}
