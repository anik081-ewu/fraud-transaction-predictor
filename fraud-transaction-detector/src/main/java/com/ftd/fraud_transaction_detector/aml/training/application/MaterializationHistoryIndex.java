package com.ftd.fraud_transaction_detector.aml.training.application;

import com.ftd.fraud_transaction_detector.aml.feature.domain.HistoricalTransaction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory, per-account transaction history loaded once for a whole materialization run.
 *
 * Replaces the per-transaction history queries that previously ran inside the
 * materialization loop. Each account's list is held in ascending transaction-date
 * order, so both "how many transactions precede this one" and "give me the last N
 * before this one" resolve with a binary search instead of a database round-trip.
 */
public final class MaterializationHistoryIndex {

    private final Map<String, List<HistoricalTransaction>> byAccount;

    private MaterializationHistoryIndex(Map<String, List<HistoricalTransaction>> byAccount) {
        this.byAccount = byAccount;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Number of transactions for this account strictly before {@code before}. */
    public long historyCount(String accountId, LocalDateTime before) {
        List<HistoricalTransaction> history = byAccount.get(accountId);
        return history == null ? 0L : lowerBound(history, before);
    }

    /** Up to {@code limit} transactions strictly before {@code before}, newest first. */
    public List<HistoricalTransaction> historyBefore(String accountId, LocalDateTime before, int limit) {
        List<HistoricalTransaction> history = byAccount.get(accountId);
        if (history == null || history.isEmpty()) return List.of();
        int end = lowerBound(history, before);
        if (end == 0) return List.of();
        int start = Math.max(0, end - limit);
        List<HistoricalTransaction> slice = new ArrayList<>(history.subList(start, end));
        Collections.reverse(slice);
        return slice;
    }

    public int accountCount() {
        return byAccount.size();
    }

    /** First index whose date is not before {@code before} — i.e. the strictly-before count. */
    private static int lowerBound(List<HistoricalTransaction> history, LocalDateTime before) {
        int low = 0;
        int high = history.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (history.get(mid).transactionDate().isBefore(before)) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    public static final class Builder {

        private final Map<String, List<HistoricalTransaction>> byAccount = new HashMap<>();

        /** Rows must arrive in ascending transaction-date order within each account. */
        public void add(String accountId, HistoricalTransaction transaction) {
            byAccount.computeIfAbsent(accountId, key -> new ArrayList<>()).add(transaction);
        }

        public MaterializationHistoryIndex build() {
            return new MaterializationHistoryIndex(byAccount);
        }
    }
}
