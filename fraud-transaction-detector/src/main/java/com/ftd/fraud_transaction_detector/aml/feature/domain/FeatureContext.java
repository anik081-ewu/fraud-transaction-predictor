package com.ftd.fraud_transaction_detector.aml.feature.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record FeatureContext(
        TransactionSnapshot currentTransaction,
        long customerHistoryCount,
        TrustedProfileSnapshot trustedProfile,
        List<HistoricalTransaction> recentTransactions,
        PeerContext peerContext
) {
    public FeatureContext(
            TransactionSnapshot currentTransaction,
            long customerHistoryCount,
            TrustedProfileSnapshot trustedProfile,
            List<HistoricalTransaction> recentTransactions
    ) {
        this(currentTransaction, customerHistoryCount, trustedProfile, recentTransactions, PeerContext.empty());
    }

    public FeatureContext {
        Objects.requireNonNull(currentTransaction, "currentTransaction is required");
        if (customerHistoryCount < 0) {
            throw new IllegalArgumentException("customerHistoryCount cannot be negative");
        }
        trustedProfile = trustedProfile == null ? TrustedProfileSnapshot.empty() : trustedProfile;
        peerContext = peerContext == null ? PeerContext.empty() : peerContext;
        recentTransactions = recentTransactions == null ? List.of() : recentTransactions;
        if (recentTransactions.stream().anyMatch(transaction ->
                !transaction.transactionDate().isBefore(currentTransaction.transactionDate()))) {
            throw new IllegalArgumentException(
                    "Feature context may contain only transactions before the current transaction"
            );
        }
        recentTransactions = recentTransactions.stream()
                .sorted(Comparator.comparing(HistoricalTransaction::transactionDate).reversed())
                .toList();
    }

    public List<HistoricalTransaction> trustedTransactions() {
        return recentTransactions.stream().filter(HistoricalTransaction::trusted).toList();
    }
}
