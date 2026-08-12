package com.ftd.fraud_transaction_detector.aml.feature.calculator;

import com.ftd.fraud_transaction_detector.aml.feature.domain.FeatureContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.ProfileFeatures;
import com.ftd.fraud_transaction_detector.aml.feature.domain.ProfileStatus;

public class ProfileConfidenceCalculator {

    public ProfileFeatures calculate(FeatureContext context) {
        long trustedCount = context.trustedProfile().transactionCount();
        return new ProfileFeatures(
                context.customerHistoryCount(),
                trustedCount,
                context.recentTransactions().size(),
                Math.min(trustedCount / 30.0, 1.0),
                statusFor(trustedCount)
        );
    }

    static ProfileStatus statusFor(long trustedCount) {
        if (trustedCount == 0) {
            return ProfileStatus.COLD_START;
        }
        if (trustedCount < 10) {
            return ProfileStatus.LOW_CONFIDENCE;
        }
        if (trustedCount < 30) {
            return ProfileStatus.DEVELOPING;
        }
        return ProfileStatus.ESTABLISHED;
    }
}
