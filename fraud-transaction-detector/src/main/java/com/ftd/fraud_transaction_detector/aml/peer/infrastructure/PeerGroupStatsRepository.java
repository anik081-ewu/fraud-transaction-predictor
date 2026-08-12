package com.ftd.fraud_transaction_detector.aml.peer.infrastructure;

import com.ftd.fraud_transaction_detector.aml.peer.domain.PeerGroupDefinition;
import com.ftd.fraud_transaction_detector.aml.peer.domain.PeerGroupStats;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class PeerGroupStatsRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PeerGroupStatsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<LocalDateTime> findLatestTrainingDataTransactionDate(LocalDateTime scoringReferenceDate) {
        String sql = """
                WITH latest_training_run AS (
                    SELECT TOP (1)
                           run.from_business_date,
                           run.to_business_date,
                           run.cutoff_timestamp,
                           run.feature_version,
                           run.model_segment
                    FROM dbo.aml_training_runs run
                    WHERE run.status IN ('CANDIDATE_READY', 'COMPLETED', 'PARTIAL')
                      AND run.dataset_path IS NOT NULL
                      AND ISNULL(run.exported_row_count, 0) > 0
                      AND run.from_business_date <= CAST(:scoringReferenceDate AS DATE)
                    ORDER BY COALESCE(run.completed_at, run.created_at) DESC,
                             run.training_run_id DESC
                )
                SELECT MAX(feature.transaction_date) AS latest_transaction_date
                FROM latest_training_run run
                INNER JOIN dbo.aml_transaction_features feature
                    ON feature.business_date BETWEEN run.from_business_date AND run.to_business_date
                   AND feature.transaction_date <= run.cutoff_timestamp
                   AND feature.transaction_date <= :scoringReferenceDate
                   AND feature.feature_version = run.feature_version
                   AND (run.model_segment IS NULL OR feature.peer_group_code = run.model_segment)
                INNER JOIN dbo.aml_feature_learning_status learning
                    ON learning.transaction_id = feature.transaction_id
                   AND learning.eligible_for_batch_training = 1
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("scoringReferenceDate", Timestamp.valueOf(scoringReferenceDate));
        return jdbc.query(sql, parameters, (rs, rowNum) -> {
            Timestamp timestamp = rs.getTimestamp("latest_transaction_date");
            return timestamp == null ? null : timestamp.toLocalDateTime();
        }).stream().filter(java.util.Objects::nonNull).findFirst();
    }

    /**
     * Returns aggregated amount and turnover stats for the peer group.
     * Looks back 90 days for amount stats and 30 days for expected monthly turnover.
     * Returns empty when fewer than 5 peer transactions exist (not enough signal).
     */
    public Optional<PeerGroupStats> findStats(PeerGroupDefinition group, LocalDateTime referenceDate) {
        String groupFilter = buildGroupFilter(group);

        String sql = """
                WITH peer_txns AS (
                    SELECT CAST(transaction_amount AS FLOAT) AS amount, account_id
                    FROM transactions
                    WHERE transaction_date >= DATEADD(day, -90, :referenceDate)
                    AND transaction_date <= :referenceDate
                    AND processing_status = 'COMPLETED'
                """ + groupFilter + """
                ),
                base_stats AS (
                    SELECT AVG(amount)  AS avg_amount,
                           STDEV(amount) AS std_amount,
                           COUNT(*)      AS sample_count
                    FROM peer_txns
                ),
                median_cte AS (
                    SELECT TOP 1
                        PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY amount) OVER () AS median_amount
                    FROM peer_txns
                ),
                monthly_turnover AS (
                    SELECT AVG(monthly_sum) AS expected_turnover
                    FROM (
                        SELECT account_id, SUM(amount) AS monthly_sum
                        FROM peer_txns
                        GROUP BY account_id
                    ) cs
                )
                SELECT b.avg_amount, m.median_amount, b.std_amount, b.sample_count, t.expected_turnover
                FROM base_stats b
                LEFT JOIN median_cte m ON 1 = 1
                CROSS JOIN monthly_turnover t
                WHERE b.sample_count >= 5
                """;

        var results = jdbc.query(sql, new MapSqlParameterSource("referenceDate", Timestamp.valueOf(referenceDate)), (rs, rowNum) ->
                new PeerGroupStats(
                        rs.getDouble("avg_amount"),
                        rs.getDouble("median_amount"),
                        rs.getDouble("std_amount"),
                        rs.getDouble("expected_turnover"),
                        rs.getLong("sample_count")
                )
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Returns the frequency percentile [0, 1] of this account within its peer group
     * based on 30-day transaction counts.
     * A value of 0.90 means the account transacted more than 90% of peers.
     */
    public Optional<Double> findFrequencyPercentile(String accountId, PeerGroupDefinition group, LocalDateTime referenceDate) {
        String groupFilter = buildGroupFilter(group);

        String sql = """
                WITH target_count AS (
                    SELECT COUNT(*) AS cnt
                    FROM transactions
                    WHERE account_id = :accountId
                    AND transaction_date >= DATEADD(day, -30, :referenceDate)
                    AND transaction_date <= :referenceDate
                    AND processing_status = 'COMPLETED'
                ),
                peer_monthly_counts AS (
                    SELECT account_id, COUNT(*) AS tx_30d
                    FROM transactions
                    WHERE transaction_date >= DATEADD(day, -30, :referenceDate)
                    AND transaction_date <= :referenceDate
                    AND processing_status = 'COMPLETED'
                """ + groupFilter + """
                    GROUP BY account_id
                )
                SELECT
                    CAST(SUM(CASE WHEN p.tx_30d <= c.cnt THEN 1 ELSE 0 END) AS FLOAT)
                    / NULLIF(COUNT(p.account_id), 0) AS frequency_percentile
                FROM peer_monthly_counts p
                CROSS JOIN target_count c
                """;

        var results = jdbc.query(sql,
                new MapSqlParameterSource("accountId", accountId).addValue("referenceDate", Timestamp.valueOf(referenceDate)),
                (rs, rowNum) -> {
                    double val = rs.getDouble("frequency_percentile");
                    return rs.wasNull() ? null : val;
                }
        );
        if (results.isEmpty() || results.get(0) == null) return Optional.empty();
        return Optional.of(results.get(0));
    }

    /**
     * Builds the SQL WHERE fragment for the given peer group.
     * Keywords come from hardcoded enum constants — not user input — so inline embedding is safe.
     */
    private String buildGroupFilter(PeerGroupDefinition group) {
        if (group.isGlobal()) return "";

        StringBuilder sb = new StringBuilder("AND (");
        boolean hasCondition = false;

        if (!group.occupationKeywords().isEmpty()) {
            sb.append("(");
            boolean first = true;
            for (String keyword : group.occupationKeywords()) {
                if (!first) sb.append(" OR ");
                // Keywords are hardcoded enum values — safe to embed
                sb.append("LOWER(customer_occupation) LIKE '%")
                  .append(keyword.toLowerCase().replace("'", "''"))
                  .append("%'");
                first = false;
            }
            sb.append(")");
            hasCondition = true;
        }

        if (group.ageFallback()) {
            if (hasCondition) sb.append(" OR ");
            sb.append("customer_age >= ").append(group.ageFallbackThreshold());
        }

        sb.append(")");

        if (group.minAge() != null) sb.append(" AND customer_age >= ").append(group.minAge());
        if (group.maxAge() != null) sb.append(" AND customer_age <= ").append(group.maxAge());

        return sb.toString();
    }
}
