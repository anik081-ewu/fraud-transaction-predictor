package com.ftd.fraud_transaction_detector.aml.feature;

import com.ftd.fraud_transaction_detector.aml.feature.domain.HistoricalTransaction;
import com.ftd.fraud_transaction_detector.aml.feature.domain.ProfileStatus;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TransactionSnapshot;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TrustedProfileSnapshot;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class FeatureFixtures {

    private FeatureFixtures() {
    }

    public static TransactionSnapshot current(String id, double amount, LocalDateTime date) {
        return new TransactionSnapshot(
                id, "CUSTOMER-1", "ACCOUNT-1", BigDecimal.valueOf(amount),
                BigDecimal.valueOf(10_000), "DEBIT", date, "MOBILE",
                "DHAKA", "BENEFICIARY-NEW", "DEVICE-NEW", 0, "SALARIED"
        );
    }

    public static HistoricalTransaction history(
            String id,
            double amount,
            LocalDateTime date,
            String beneficiary,
            boolean trusted
    ) {
        return new HistoricalTransaction(
                id, BigDecimal.valueOf(amount), date, "DEBIT", "BRANCH",
                "DHAKA", beneficiary, "DEVICE-1", trusted
        );
    }

    public static TrustedProfileSnapshot trustedProfile(long count) {
        return new TrustedProfileSnapshot(
                count, 100.0, 100.0, 10.0, 150.0, 50.0,
                8, 20, "BRANCH", "DHAKA",
                Math.min(count / 30.0, 1.0),
                count >= 30 ? ProfileStatus.ESTABLISHED : ProfileStatus.DEVELOPING
        );
    }
}
