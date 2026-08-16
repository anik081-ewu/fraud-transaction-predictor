package com.ftd.fraud_transaction_detector.aml.training.application;

import com.ftd.fraud_transaction_detector.aml.feature.domain.TerminalRiskContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TerminalWindowStatistics;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TerminalRiskHistoryIndex {

    private static final String WEAK_NEGATIVE_SOURCE = "AUTO_NO_CASE";

    private final Map<String, Series> byTerminal;
    private final Series global;

    private TerminalRiskHistoryIndex(Map<String, Series> byTerminal, Series global) {
        this.byTerminal = byTerminal;
        this.global = global;
    }

    public static Builder builder() {
        return new Builder();
    }

    public TerminalRiskContext context(
            String terminal,
            LocalDateTime asOf,
            int delayDays,
            double smoothingStrength,
            int minimumTransactions,
            boolean enabled
    ) {
        if (!enabled || terminal == null || terminal.isBlank()) {
            return TerminalRiskContext.disabled();
        }
        Series terminalSeries = byTerminal.getOrDefault(terminal.trim(), Series.empty());
        LocalDateTime labelCutoff = asOf.minusDays(delayDays);
        double globalRate = global.confirmedRate(labelCutoff.minusDays(30), labelCutoff);
        return new TerminalRiskContext(
                true,
                terminalSeries.statistics(asOf, labelCutoff, 1),
                terminalSeries.statistics(asOf, labelCutoff, 7),
                terminalSeries.statistics(asOf, labelCutoff, 30),
                globalRate,
                smoothingStrength,
                minimumTransactions
        );
    }

    public int terminalCount() {
        return byTerminal.size();
    }

    public static final class Builder {
        private final Map<String, List<Entry>> byTerminal = new HashMap<>();
        private final List<Entry> global = new ArrayList<>();

        public void add(
                String terminal,
                LocalDateTime transactionDate,
                BigDecimal amount,
                Boolean fraudLabel,
                String labelSource
        ) {
            if (terminal == null || terminal.isBlank() || transactionDate == null || amount == null) return;
            boolean confirmed = fraudLabel != null && !WEAK_NEGATIVE_SOURCE.equalsIgnoreCase(normalize(labelSource));
            Entry entry = new Entry(transactionDate, amount.doubleValue(), confirmed, Boolean.TRUE.equals(fraudLabel));
            byTerminal.computeIfAbsent(terminal.trim(), ignored -> new ArrayList<>()).add(entry);
            global.add(entry);
        }

        public TerminalRiskHistoryIndex build() {
            Map<String, Series> terminalSeries = new HashMap<>();
            byTerminal.forEach((terminal, entries) -> terminalSeries.put(terminal, Series.of(entries)));
            return new TerminalRiskHistoryIndex(Map.copyOf(terminalSeries), Series.of(global));
        }

        private String normalize(String value) {
            return value == null ? "" : value.trim();
        }
    }

    private record Entry(LocalDateTime date, double amount, boolean confirmed, boolean fraud) {
    }

    private static final class Series {
        private final List<Entry> entries;
        private final double[] amountPrefix;
        private final long[] confirmedPrefix;
        private final long[] fraudPrefix;

        private Series(List<Entry> entries) {
            this.entries = entries;
            this.amountPrefix = new double[entries.size() + 1];
            this.confirmedPrefix = new long[entries.size() + 1];
            this.fraudPrefix = new long[entries.size() + 1];
            for (int index = 0; index < entries.size(); index++) {
                Entry entry = entries.get(index);
                amountPrefix[index + 1] = amountPrefix[index] + entry.amount();
                confirmedPrefix[index + 1] = confirmedPrefix[index] + (entry.confirmed() ? 1 : 0);
                fraudPrefix[index + 1] = fraudPrefix[index] + (entry.confirmed() && entry.fraud() ? 1 : 0);
            }
        }

        static Series of(List<Entry> source) {
            List<Entry> sorted = source.stream().sorted(Comparator.comparing(Entry::date)).toList();
            return new Series(sorted);
        }

        static Series empty() {
            return new Series(List.of());
        }

        TerminalWindowStatistics statistics(LocalDateTime asOf, LocalDateTime labelCutoff, int days) {
            int transactionStart = lowerBound(asOf.minusDays(days));
            int transactionEnd = lowerBound(asOf);
            int transactionCount = transactionEnd - transactionStart;
            double averageAmount = transactionCount == 0
                    ? 0.0
                    : (amountPrefix[transactionEnd] - amountPrefix[transactionStart]) / transactionCount;

            int labelStart = lowerBound(labelCutoff.minusDays(days));
            int labelEnd = lowerBound(labelCutoff);
            return new TerminalWindowStatistics(
                    transactionCount,
                    averageAmount,
                    confirmedPrefix[labelEnd] - confirmedPrefix[labelStart],
                    fraudPrefix[labelEnd] - fraudPrefix[labelStart]
            );
        }

        double confirmedRate(LocalDateTime start, LocalDateTime end) {
            int from = lowerBound(start);
            int to = lowerBound(end);
            long labels = confirmedPrefix[to] - confirmedPrefix[from];
            return labels == 0 ? 0.0 : (double) (fraudPrefix[to] - fraudPrefix[from]) / labels;
        }

        private int lowerBound(LocalDateTime target) {
            int low = 0;
            int high = entries.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (entries.get(middle).date().isBefore(target)) low = middle + 1;
                else high = middle;
            }
            return low;
        }
    }
}
