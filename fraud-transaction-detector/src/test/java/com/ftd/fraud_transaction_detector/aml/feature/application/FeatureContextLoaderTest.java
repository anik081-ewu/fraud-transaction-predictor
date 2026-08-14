package com.ftd.fraud_transaction_detector.aml.feature.application;

import com.ftd.fraud_transaction_detector.aml.profile.infrastructure.CustomerProfileRepository;
import com.ftd.fraud_transaction_detector.aml.peer.application.PeerContextLoader;
import com.ftd.fraud_transaction_detector.aml.feature.domain.PeerContext;
import com.ftd.fraud_transaction_detector.transactions.entity.Transaction;
import com.ftd.fraud_transaction_detector.transactions.repo.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeatureContextLoaderTest {

    @Test
    void loadsOnlyTransactionsBeforeTheCurrentEventTime() {
        TransactionRepository repository = mock(TransactionRepository.class);
        CustomerProfileRepository profileRepository = mock(CustomerProfileRepository.class);
        PeerContextLoader peerContextLoader = mock(PeerContextLoader.class);
        LocalDateTime currentTime = LocalDateTime.of(2026, 8, 4, 12, 0);
        Transaction current = transaction("T4", 400, currentTime);
        current.setCustomerId("ACCOUNT-1");
        Transaction prior = transaction("T3", 300, currentTime.minusHours(1));

        when(repository.countByAccountIdAndTransactionDateLessThan("ACCOUNT-1", currentTime)).thenReturn(3L);
        when(profileRepository.findRecentBefore("ACCOUNT-1", currentTime, 30)).thenReturn(List.of());
        when(repository.findTop30ByAccountIdAndTransactionDateLessThanOrderByTransactionDateDesc(
                "ACCOUNT-1", currentTime
        )).thenReturn(List.of(prior));
        when(peerContextLoader.load(
                current.getCustomerOccupation(), current.getCustomerAge(), "ACCOUNT-1",
                null, currentTime
        )).thenReturn(PeerContext.empty());

        var context = new FeatureContextLoader(repository, profileRepository, peerContextLoader).load(current);

        assertEquals("T4", context.currentTransaction().transactionId());
        assertEquals(List.of("T3"), context.recentTransactions().stream()
                .map(item -> item.transactionId())
                .toList());
        verify(repository).findTop30ByAccountIdAndTransactionDateLessThanOrderByTransactionDateDesc(
                "ACCOUNT-1", currentTime
        );
        verify(profileRepository).findTrusted("ACCOUNT-1");
    }

    private static Transaction transaction(String id, double amount, LocalDateTime date) {
        Transaction transaction = new Transaction();
        transaction.setTransactionId(id);
        transaction.setAccountId("ACCOUNT-1");
        transaction.setTransactionAmount(BigDecimal.valueOf(amount));
        transaction.setAccountBalance(BigDecimal.valueOf(10_000));
        transaction.setTransactionType("DEBIT");
        transaction.setTransactionDate(date);
        transaction.setChannel("MOBILE");
        transaction.setLocation("DHAKA");
        return transaction;
    }
}
