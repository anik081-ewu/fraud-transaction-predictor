package com.ftd.fraud_transaction_detector.aml.training.infrastructure;

import com.ftd.fraud_transaction_detector.aml.feature.domain.HistoricalTransaction;
import com.ftd.fraud_transaction_detector.aml.training.application.MaterializationHistoryIndex;
import com.ftd.fraud_transaction_detector.aml.training.application.TerminalRiskHistoryIndex;
import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingRun;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class HistoricalFeatureMaterializationRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public HistoricalFeatureMaterializationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Next batch of transactions that still need a feature row.
     *
     * The (afterDate, afterId) keyset cursor stops each batch from re-scanning the rows
     * already materialized by earlier batches — without it the scan cost grows
     * quadratically with the number of batches. The LEFT JOIN is retained so a rerun of
     * a partially materialized run still skips rows that already have features.
     */
    public List<Long> findMissingTransactionIds(AmlTrainingRun run, int limit, LocalDateTime afterDate, long afterId) {
        return jdbcTemplate.queryForList("""
                SELECT TOP (:limit) transaction_row.id
                FROM dbo.transactions transaction_row
                LEFT JOIN dbo.aml_transaction_features feature
                    ON feature.transaction_id = transaction_row.transaction_id
                   AND feature.feature_version = :featureVersion
                WHERE COALESCE(transaction_row.business_date, CAST(transaction_row.transaction_date AS DATE))
                    BETWEEN :fromDate AND :toDate
                  AND transaction_row.transaction_date <= :cutoffTimestamp
                  AND feature.transaction_id IS NULL
                  AND (transaction_row.transaction_date > :afterDate
                       OR (transaction_row.transaction_date = :afterDate AND transaction_row.id > :afterId))
                ORDER BY transaction_row.transaction_date, transaction_row.id
                """, parameters(run)
                        .addValue("limit", limit)
                        .addValue("afterDate", Timestamp.valueOf(afterDate))
                        .addValue("afterId", afterId),
                Long.class);
    }

    public void normalizeTransactions(AmlTrainingRun run) {
        jdbcTemplate.update("""
                UPDATE transaction_row
                SET customer_id = COALESCE(NULLIF(transaction_row.customer_id, ''), transaction_row.account_id),
                    business_date = COALESCE(transaction_row.business_date, CAST(transaction_row.transaction_date AS DATE)),
                    updated_at = SYSUTCDATETIME()
                FROM dbo.transactions transaction_row
                WHERE CAST(transaction_row.transaction_date AS DATE) BETWEEN :fromDate AND :toDate
                  AND transaction_row.transaction_date <= :cutoffTimestamp
                  AND (
                      transaction_row.customer_id IS NULL OR transaction_row.customer_id = ''
                      OR transaction_row.business_date IS NULL
                  )
                """, parameters(run));
    }

    public void insertMissingLearningEligibility(AmlTrainingRun run) {
        jdbcTemplate.update("""
                INSERT INTO dbo.aml_feature_learning_status (
                    transaction_id, eligibility_status, eligibility_reason,
                    eligible_for_incremental_model, eligible_for_trusted_profile,
                    eligible_for_batch_training, reviewed_by, reviewed_at, updated_at
                )
                SELECT
                    feature.transaction_id,
                    CASE
                        WHEN EXISTS (
                            SELECT 1 FROM dbo.fraud_alerts alert
                            WHERE alert.transaction_id = feature.transaction_id
                              AND alert.review_status = 'STR_GENERATED'
                        ) THEN 'DO_NOT_LEARN'
                        WHEN EXISTS (
                            SELECT 1 FROM dbo.fraud_alerts alert
                            WHERE alert.transaction_id = feature.transaction_id
                              AND COALESCE(alert.review_status, 'PENDING') <> 'FALSE_POSITIVE'
                        ) THEN 'WAIT_FOR_REVIEW'
                        ELSE 'LEARN_IMMEDIATELY'
                    END,
                    CASE
                        WHEN EXISTS (
                            SELECT 1 FROM dbo.fraud_alerts alert
                            WHERE alert.transaction_id = feature.transaction_id
                              AND alert.review_status = 'STR_GENERATED'
                        ) THEN 'Historical STR generated; excluded from learning'
                        WHEN EXISTS (
                            SELECT 1 FROM dbo.fraud_alerts alert
                            WHERE alert.transaction_id = feature.transaction_id
                              AND COALESCE(alert.review_status, 'PENDING') <> 'FALSE_POSITIVE'
                        ) THEN 'Historical alert requires review before learning'
                        ELSE 'Historical transaction accepted for batch learning'
                    END,
                    CASE WHEN EXISTS (
                        SELECT 1 FROM dbo.fraud_alerts alert
                        WHERE alert.transaction_id = feature.transaction_id
                          AND COALESCE(alert.review_status, 'PENDING') <> 'FALSE_POSITIVE'
                    ) THEN 0 ELSE 1 END,
                    CASE WHEN EXISTS (
                        SELECT 1 FROM dbo.fraud_alerts alert
                        WHERE alert.transaction_id = feature.transaction_id
                          AND COALESCE(alert.review_status, 'PENDING') <> 'FALSE_POSITIVE'
                    ) THEN 0 ELSE 1 END,
                    CASE WHEN EXISTS (
                        SELECT 1 FROM dbo.fraud_alerts alert
                        WHERE alert.transaction_id = feature.transaction_id
                          AND COALESCE(alert.review_status, 'PENDING') <> 'FALSE_POSITIVE'
                    ) THEN 0 ELSE 1 END,
                    reviewed.reviewed_by,
                    reviewed.reviewed_at,
                    SYSUTCDATETIME()
                FROM dbo.aml_transaction_features feature
                INNER JOIN dbo.transactions transaction_row
                    ON transaction_row.transaction_id = feature.transaction_id
                OUTER APPLY (
                    SELECT TOP (1) alert.reviewed_by, alert.reviewed_at
                    FROM dbo.fraud_alerts alert
                    WHERE alert.transaction_id = feature.transaction_id
                      AND alert.review_status IN ('FALSE_POSITIVE', 'STR_GENERATED')
                    ORDER BY alert.reviewed_at DESC, alert.id DESC
                ) reviewed
                WHERE feature.business_date BETWEEN :fromDate AND :toDate
                  AND feature.transaction_date <= :cutoffTimestamp
                  AND feature.feature_version = :featureVersion
                  AND NOT EXISTS (
                      SELECT 1 FROM dbo.aml_feature_learning_status learning
                      WHERE learning.transaction_id = feature.transaction_id
                  )
                """, parameters(run));
    }

    /**
     * Streams every transaction up to the run's cutoff into a per-account history index.
     *
     * One query replaces the three per-transaction history lookups that previously ran
     * inside the materialization loop. The cutoff bound is the only filter needed: every
     * transaction that could precede a materialized row is at or below the same cutoff,
     * so the resulting counts and last-N windows match the old per-row queries exactly.
     */
    public MaterializationHistoryIndex loadAccountHistory(AmlTrainingRun run) {
        MaterializationHistoryIndex.Builder builder = MaterializationHistoryIndex.builder();
        jdbcTemplate.query("""
                SELECT transaction_row.account_id,
                       transaction_row.transaction_id,
                       transaction_row.transaction_amount,
                       transaction_row.transaction_date,
                       transaction_row.transaction_type,
                       transaction_row.channel,
                       transaction_row.location
                FROM dbo.transactions transaction_row
                WHERE transaction_row.transaction_date <= :cutoffTimestamp
                ORDER BY transaction_row.account_id, transaction_row.transaction_date, transaction_row.id
                """,
                new MapSqlParameterSource("cutoffTimestamp", run.cutoffTimestamp()),
                (ResultSet resultSet) -> {
                    BigDecimal amount = resultSet.getBigDecimal("transaction_amount");
                    Timestamp date = resultSet.getTimestamp("transaction_date");
                    if (amount == null || date == null) return;
                    builder.add(resultSet.getString("account_id"), new HistoricalTransaction(
                            resultSet.getString("transaction_id"),
                            amount,
                            date.toLocalDateTime(),
                            resultSet.getString("transaction_type"),
                            resultSet.getString("channel"),
                            resultSet.getString("location"),
                            null,
                            null,
                            true
                    ));
                });
        return builder.build();
    }

    public TerminalRiskHistoryIndex loadTerminalRiskHistory(AmlTrainingRun run) {
        TerminalRiskHistoryIndex.Builder builder = TerminalRiskHistoryIndex.builder();
        jdbcTemplate.query("""
                SELECT transaction_row.location,
                       transaction_row.transaction_date,
                       transaction_row.transaction_amount,
                       transaction_row.fraud_label,
                       transaction_row.label_source
                FROM dbo.transactions transaction_row
                WHERE transaction_row.transaction_date <= :cutoffTimestamp
                  AND transaction_row.location IS NOT NULL
                ORDER BY transaction_row.location, transaction_row.transaction_date, transaction_row.id
                """,
                new MapSqlParameterSource("cutoffTimestamp", run.cutoffTimestamp()),
                (ResultSet resultSet) -> {
                    Timestamp date = resultSet.getTimestamp("transaction_date");
                    BigDecimal amount = resultSet.getBigDecimal("transaction_amount");
                    if (date == null || amount == null) return;
                    boolean fraud = resultSet.getBoolean("fraud_label");
                    Boolean fraudLabel = resultSet.wasNull() ? null : fraud;
                    builder.add(
                            resultSet.getString("location"),
                            date.toLocalDateTime(),
                            amount,
                            fraudLabel,
                            resultSet.getString("label_source")
                    );
                });
        return builder.build();
    }

    /** How many transactions in the run's window still need a feature row — the progress-bar total. */
    public long countMissingTransactions(AmlTrainingRun run) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT_BIG(*)
                FROM dbo.transactions transaction_row
                LEFT JOIN dbo.aml_transaction_features feature
                    ON feature.transaction_id = transaction_row.transaction_id
                   AND feature.feature_version = :featureVersion
                WHERE COALESCE(transaction_row.business_date, CAST(transaction_row.transaction_date AS DATE))
                    BETWEEN :fromDate AND :toDate
                  AND transaction_row.transaction_date <= :cutoffTimestamp
                  AND feature.transaction_id IS NULL
                """, parameters(run), Long.class);
        return count == null ? 0L : count;
    }

    public void markCompleted(List<Long> rowIds) {
        jdbcTemplate.update(
                "UPDATE dbo.transactions SET feature_status = 'COMPLETED', updated_at = SYSUTCDATETIME() WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", rowIds)
        );
    }

    private MapSqlParameterSource parameters(AmlTrainingRun run) {
        return new MapSqlParameterSource()
                .addValue("fromDate", run.fromBusinessDate())
                .addValue("toDate", run.toBusinessDate())
                .addValue("cutoffTimestamp", run.cutoffTimestamp())
                .addValue("featureVersion", run.featureVersion());
    }
}
