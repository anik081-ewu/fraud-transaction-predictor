package com.ftd.fraud_transaction_detector.aml.feature.application;

import com.ftd.fraud_transaction_detector.aml.feature.domain.FeatureContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.HistoricalTransaction;
import com.ftd.fraud_transaction_detector.aml.feature.domain.PeerContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.ProfileStatus;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TransactionSnapshot;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TrustedProfileSnapshot;
import com.ftd.fraud_transaction_detector.aml.peer.application.PeerContextLoader;
import com.ftd.fraud_transaction_detector.aml.profile.infrastructure.CustomerProfileRepository;
import com.ftd.fraud_transaction_detector.transactions.entity.Transaction;
import com.ftd.fraud_transaction_detector.transactions.repo.TransactionRepository;
import org.springframework.stereotype.Service;

import com.ftd.fraud_transaction_detector.aml.peer.domain.PeerGroupStats;
import com.ftd.fraud_transaction_detector.aml.profile.domain.TrustedCustomerProfile;
import com.ftd.fraud_transaction_detector.aml.training.application.MaterializationHistoryIndex;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class FeatureContextLoader {

    private final TransactionRepository transactionRepository;
    private final CustomerProfileRepository profileRepository;
    private final PeerContextLoader peerContextLoader;

    public FeatureContextLoader(
            TransactionRepository transactionRepository,
            CustomerProfileRepository profileRepository,
            PeerContextLoader peerContextLoader
    ) {
        this.transactionRepository = transactionRepository;
        this.profileRepository = profileRepository;
        this.peerContextLoader = peerContextLoader;
    }

    public FeatureContext load(Transaction transaction) {
        long historyCount = transactionRepository.countByAccountIdAndTransactionDateLessThan(
                transaction.getAccountId(), transaction.getTransactionDate()
        );
        String customerId = transaction.getCustomerId() == null
                ? transaction.getAccountId()
                : transaction.getCustomerId();
        List<HistoricalTransaction> history = profileRepository.findRecentBefore(
                customerId, transaction.getTransactionDate(), 30
        );
        if (history.isEmpty()) {
            history = transactionRepository
                    .findTop30ByAccountIdAndTransactionDateLessThanOrderByTransactionDateDesc(
                            transaction.getAccountId(), transaction.getTransactionDate()
                    )
                    .stream()
                    .map(this::toHistory)
                    .toList();
        }
        TrustedProfileSnapshot trustedProfile = profileRepository.findTrusted(customerId)
                .filter(profile -> profile.isPointInTimeSafe(transaction.getTransactionDate()))
                .map(profile -> profile.snapshot())
                .orElseGet(() -> transitionalTrustedProfile(historyCount));

        PeerContext peerContext = peerContextLoader.load(
                transaction.getCustomerOccupation(),
                transaction.getCustomerAge(),
                transaction.getAccountId(),
                trustedProfile,
                transaction.getTransactionDate()
        );

        return new FeatureContext(
                toCurrent(transaction),
                historyCount,
                trustedProfile,
                history,
                peerContext
        );
    }

    /**
     * Batch-materialization variant: resolves history from a pre-loaded in-memory index
     * and reuses cached peer/profile lookups, so no per-transaction history query runs.
     *
     * Feature values match {@link #load(Transaction)} for bulk-uploaded data, where
     * aml_customer_recent_transactions is empty and the account-history fallback is
     * the path that would have been taken anyway.
     *
     * @param profileCache keyed by customerId — caches only the row lookup, not the
     *                     derived snapshot, because the cold-start fallback depends on
     *                     each transaction's own history count.
     */
    public FeatureContext load(
            Transaction transaction,
            MaterializationHistoryIndex historyIndex,
            Map<String, Optional<TrustedCustomerProfile>> profileCache,
            Map<String, PeerGroupStats> peerStatsCache,
            Map<String, Double> peerPercentileCache
    ) {
        String accountId = transaction.getAccountId();
        LocalDateTime transactionDate = transaction.getTransactionDate();
        String customerId = transaction.getCustomerId() == null ? accountId : transaction.getCustomerId();

        long historyCount = historyIndex.historyCount(accountId, transactionDate);
        List<HistoricalTransaction> history = historyIndex.historyBefore(accountId, transactionDate, 30);

        TrustedProfileSnapshot trustedProfile = profileCache
                .computeIfAbsent(customerId, profileRepository::findTrusted)
                .filter(profile -> profile.isPointInTimeSafe(transactionDate))
                .map(profile -> profile.snapshot())
                .orElseGet(() -> transitionalTrustedProfile(historyCount));

        PeerContext peerContext = peerContextLoader.loadWithCache(
                transaction.getCustomerOccupation(),
                transaction.getCustomerAge(),
                accountId,
                trustedProfile,
                transactionDate,
                peerStatsCache,
                peerPercentileCache
        );
        return new FeatureContext(toCurrent(transaction), historyCount, trustedProfile, history, peerContext);
    }

    private TransactionSnapshot toCurrent(Transaction transaction) {
        return new TransactionSnapshot(
                transaction.getTransactionId(),
                transaction.getCustomerId(),
                transaction.getAccountId(),
                transaction.getTransactionAmount(),
                transaction.getAccountBalance(),
                transaction.getTransactionType(),
                transaction.getTransactionDate(),
                transaction.getChannel(),
                transaction.getLocation(),
                null,
                null,
                transaction.getLoginAttempts() == null ? 0 : transaction.getLoginAttempts(),
                transaction.getCustomerOccupation()
        );
    }

    private HistoricalTransaction toHistory(Transaction transaction) {
        return new HistoricalTransaction(
                transaction.getTransactionId(),
                transaction.getTransactionAmount(),
                transaction.getTransactionDate(),
                transaction.getTransactionType(),
                transaction.getChannel(),
                transaction.getLocation(),
                null,
                null,
                true
        );
    }

    private TrustedProfileSnapshot transitionalTrustedProfile(long historyCount) {
        return new TrustedProfileSnapshot(
                historyCount, null, null, null, null, null,
                null, null, null, null,
                Math.min(historyCount / 30.0, 1.0),
                historyCount == 0 ? ProfileStatus.COLD_START
                        : historyCount < 10 ? ProfileStatus.LOW_CONFIDENCE
                        : historyCount < 30 ? ProfileStatus.DEVELOPING
                        : ProfileStatus.ESTABLISHED
        );
    }
}
