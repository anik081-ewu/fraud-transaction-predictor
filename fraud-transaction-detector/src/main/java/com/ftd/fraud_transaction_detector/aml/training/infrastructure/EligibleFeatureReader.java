package com.ftd.fraud_transaction_detector.aml.training.infrastructure;

import com.ftd.fraud_transaction_detector.aml.training.domain.AmlTrainingRun;
import com.ftd.fraud_transaction_detector.aml.training.domain.ExportFeatureRow;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.time.LocalDateTime;

@Repository
public class EligibleFeatureReader {

    private static final String FILTER = """
            f.business_date BETWEEN :fromDate AND :toDate
            AND f.transaction_date <= :cutoffTimestamp
            AND f.feature_version = :featureVersion
            AND learning.eligible_for_batch_training = 1
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public EligibleFeatureReader(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long count(AmlTrainingRun run) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT_BIG(*)
                FROM dbo.aml_transaction_features f
                INNER JOIN dbo.aml_feature_learning_status learning
                    ON learning.transaction_id = f.transaction_id
                WHERE
                """ + filter(run), parameters(run, LocalDateTime.of(1, 1, 1, 0, 0), 0, 1), Long.class);
        return count == null ? 0 : count;
    }

    public List<ExportFeatureRow> readAfter(
            AmlTrainingRun run,
            LocalDateTime lastTransactionDate,
            long lastId,
            int limit
    ) {
        String sql = """
                SELECT TOP (:limit)
                    f.id, f.transaction_id, f.customer_id, f.account_id,
                    f.business_date, f.transaction_date, f.feature_version,
                    f.model_feature_schema, f.model_features_json,
                    f.current_amount, f.current_balance, f.amount_balance_ratio,
                    f.transaction_hour, f.transaction_day_of_week, f.is_night, f.is_weekend,
                    f.customer_history_count, f.trusted_history_count,
                    f.recent_transaction_count, f.profile_confidence,
                    f.last_30_avg_amount, f.last_30_median_amount, f.last_30_std_amount,
                    f.amount_vs_last_30_avg, f.amount_vs_last_30_median, f.amount_z_score_last_30,
                    f.transaction_count_1h, f.transaction_count_24h,
                    f.transaction_count_7d, f.transaction_count_30d,
                    f.amount_sum_24h, f.amount_sum_7d, f.amount_sum_30d,
                    f.new_beneficiary, f.new_location, f.new_channel, f.new_device,
                    f.unusual_transaction_hour, f.peer_group_code, f.peer_avg_amount,
                    f.peer_std_amount, f.amount_vs_peer_avg, f.peer_amount_z_score,
                    tx.fraud_label
                FROM dbo.aml_transaction_features f
                INNER JOIN dbo.aml_feature_learning_status learning
                    ON learning.transaction_id = f.transaction_id
                INNER JOIN dbo.transactions tx
                    ON tx.transaction_id = f.transaction_id
                WHERE
                """ + filter(run) + """
                  AND (f.transaction_date > :lastTransactionDate
                       OR (f.transaction_date = :lastTransactionDate AND f.id > :lastId))
                ORDER BY f.transaction_date, f.id
                """;
        return jdbcTemplate.query(sql, parameters(run, lastTransactionDate, lastId, limit), this::mapRow);
    }

    private String filter(AmlTrainingRun run) {
        return FILTER + (run.modelSegment() == null ? "" : " AND f.peer_group_code = :modelSegment\n");
    }

    private MapSqlParameterSource parameters(
            AmlTrainingRun run,
            LocalDateTime lastTransactionDate,
            long lastId,
            int limit
    ) {
        return new MapSqlParameterSource()
                .addValue("fromDate", run.fromBusinessDate()).addValue("toDate", run.toBusinessDate())
                .addValue("cutoffTimestamp", run.cutoffTimestamp()).addValue("featureVersion", run.featureVersion())
                .addValue("modelSegment", run.modelSegment()).addValue("lastTransactionDate", lastTransactionDate)
                .addValue("lastId", lastId).addValue("limit", limit);
    }

    private ExportFeatureRow mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ExportFeatureRow(
                resultSet.getLong("id"), resultSet.getString("transaction_id"),
                resultSet.getString("customer_id"), resultSet.getString("account_id"),
                resultSet.getDate("business_date").toLocalDate(),
                resultSet.getTimestamp("transaction_date").toLocalDateTime(),
                resultSet.getString("feature_version"), resultSet.getString("model_feature_schema"),
                resultSet.getString("model_features_json"), resultSet.getDouble("current_amount"),
                nullableDouble(resultSet, "current_balance"), nullableDouble(resultSet, "amount_balance_ratio"),
                resultSet.getInt("transaction_hour"), resultSet.getInt("transaction_day_of_week"),
                resultSet.getBoolean("is_night"), resultSet.getBoolean("is_weekend"),
                resultSet.getLong("customer_history_count"), resultSet.getLong("trusted_history_count"),
                resultSet.getInt("recent_transaction_count"), resultSet.getDouble("profile_confidence"),
                nullableDouble(resultSet, "last_30_avg_amount"), nullableDouble(resultSet, "last_30_median_amount"),
                nullableDouble(resultSet, "last_30_std_amount"), nullableDouble(resultSet, "amount_vs_last_30_avg"),
                nullableDouble(resultSet, "amount_vs_last_30_median"), nullableDouble(resultSet, "amount_z_score_last_30"),
                resultSet.getInt("transaction_count_1h"), resultSet.getInt("transaction_count_24h"),
                resultSet.getInt("transaction_count_7d"), resultSet.getInt("transaction_count_30d"),
                resultSet.getDouble("amount_sum_24h"), resultSet.getDouble("amount_sum_7d"),
                resultSet.getDouble("amount_sum_30d"), resultSet.getBoolean("new_beneficiary"),
                resultSet.getBoolean("new_location"), resultSet.getBoolean("new_channel"),
                resultSet.getBoolean("new_device"), resultSet.getBoolean("unusual_transaction_hour"),
                resultSet.getString("peer_group_code"), nullableDouble(resultSet, "peer_avg_amount"),
                nullableDouble(resultSet, "peer_std_amount"), nullableDouble(resultSet, "amount_vs_peer_avg"),
                nullableDouble(resultSet, "peer_amount_z_score"),
                nullableBoolean(resultSet, "fraud_label")
        );
    }

    private static Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Boolean nullableBoolean(ResultSet resultSet, String column) throws SQLException {
        boolean value = resultSet.getBoolean(column);
        return resultSet.wasNull() ? null : value;
    }
}
