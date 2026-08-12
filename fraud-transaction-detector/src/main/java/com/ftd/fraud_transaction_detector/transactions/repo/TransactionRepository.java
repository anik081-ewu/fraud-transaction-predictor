package com.ftd.fraud_transaction_detector.transactions.repo;

import com.ftd.fraud_transaction_detector.transactions.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Page<Transaction> findByUploadBatchId(Long uploadBatchId, Pageable pageable);

    Page<Transaction> findByTransactionIdContainingIgnoreCaseOrAccountIdContainingIgnoreCase(
            String transactionId,
            String accountId,
            Pageable pageable
    );

    Optional<Transaction> findByTransactionId(String transactionId);

    Page<Transaction> findByIdLessThanEqual(Long maximumId, Pageable pageable);

    long countByIdLessThanEqual(Long maximumId);

    @Query("select max(transaction.id) from Transaction transaction")
    Optional<Long> findMaximumId();

    boolean existsByTransactionId(String transactionId);

    Optional<Transaction> findFirstByAccountIdAndTransactionDateLessThanOrderByTransactionDateDesc(String accountId, LocalDateTime beforeDate);

    long countByAccountIdAndTransactionDateLessThan(String accountId, LocalDateTime beforeDate);

    List<Transaction> findTop30ByAccountIdAndTransactionDateLessThanOrderByTransactionDateDesc(
            String accountId,
            LocalDateTime beforeDate
    );

    @Query(value = """
            select
              cast(avg(cast(transaction_amount as float)) as decimal(18,2)) as avg_amount,
              cast(max(transaction_amount) as decimal(18,2)) as max_amount,
              cast(stdev(cast(transaction_amount as float)) as decimal(18,2)) as std_amount
            from transactions
            where account_id = :accountId and transaction_date < :beforeDate
            """, nativeQuery = true)
    AmountStatsRow amountStats(@org.springframework.data.repository.query.Param("accountId") String accountId,
                               @org.springframework.data.repository.query.Param("beforeDate") LocalDateTime beforeDate);

    @Query(value = """
            select cast(avg(cast(transaction_amount as float)) as decimal(18,2)) as avg_amount
            from transactions
            where account_id = :accountId
              and transaction_date >= dateadd(day, -:days, :beforeDate)
              and transaction_date < :beforeDate
            """, nativeQuery = true)
    BigDecimal rollingAvg(@org.springframework.data.repository.query.Param("accountId") String accountId,
                          @org.springframework.data.repository.query.Param("beforeDate") LocalDateTime beforeDate,
                          @org.springframework.data.repository.query.Param("days") int days);

    @Query("""
            select transaction
            from Transaction transaction
            where transaction.businessDate between :fromDate and :toDate
              and transaction.transactionDate <= :cutoffTimestamp
            order by transaction.transactionDate asc, transaction.id asc
            """)
    List<Transaction> findEligibleForTraining(
            @org.springframework.data.repository.query.Param("fromDate") java.time.LocalDate fromDate,
            @org.springframework.data.repository.query.Param("toDate") java.time.LocalDate toDate,
            @org.springframework.data.repository.query.Param("cutoffTimestamp") LocalDateTime cutoffTimestamp
    );

    @Query(value = "SELECT CONVERT(varchar(10), MIN(COALESCE(business_date, CAST(transaction_date AS DATE))), 23) FROM dbo.transactions WHERE upload_batch_id = :batchId", nativeQuery = true)
    String findMinBusinessDateByUploadBatchId(@org.springframework.data.repository.query.Param("batchId") Long batchId);

    @Query(value = "SELECT CONVERT(varchar(10), MAX(COALESCE(business_date, CAST(transaction_date AS DATE))), 23) FROM dbo.transactions WHERE upload_batch_id = :batchId", nativeQuery = true)
    String findMaxBusinessDateByUploadBatchId(@org.springframework.data.repository.query.Param("batchId") Long batchId);

    interface AmountStatsRow {
        BigDecimal getAvgAmount();

        BigDecimal getMaxAmount();

        BigDecimal getStdAmount();
    }
}
