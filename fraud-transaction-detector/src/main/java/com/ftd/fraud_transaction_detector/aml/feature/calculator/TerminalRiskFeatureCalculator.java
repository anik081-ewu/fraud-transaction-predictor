package com.ftd.fraud_transaction_detector.aml.feature.calculator;

import com.ftd.fraud_transaction_detector.aml.feature.domain.TerminalRiskContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TerminalRiskFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TerminalWindowStatistics;

public class TerminalRiskFeatureCalculator {

    public TerminalRiskFeatures calculate(TerminalRiskContext context) {
        if (context == null || !context.enabled()) {
            return TerminalRiskFeatures.empty();
        }
        TerminalWindowStatistics oneDay = context.oneDay();
        TerminalWindowStatistics sevenDays = context.sevenDays();
        TerminalWindowStatistics thirtyDays = context.thirtyDays();
        return new TerminalRiskFeatures(
                oneDay.transactionCount(), oneDay.confirmedFraudCount(), smooth(oneDay, context), oneDay.averageAmount(),
                sevenDays.transactionCount(), sevenDays.confirmedFraudCount(), smooth(sevenDays, context), sevenDays.averageAmount(),
                thirtyDays.transactionCount(), thirtyDays.confirmedFraudCount(), smooth(thirtyDays, context), thirtyDays.averageAmount(),
                thirtyDays.transactionCount() >= context.minimumTransactions()
        );
    }

    private double smooth(TerminalWindowStatistics statistics, TerminalRiskContext context) {
        double numerator = statistics.confirmedFraudCount()
                + context.smoothingStrength() * context.globalConfirmedFraudRate();
        double denominator = statistics.confirmedLabelCount() + context.smoothingStrength();
        return denominator <= 0.0 ? context.globalConfirmedFraudRate() : numerator / denominator;
    }
}
