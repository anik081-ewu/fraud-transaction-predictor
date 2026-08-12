package com.ftd.fraud_transaction_detector.aml.profile.infrastructure;

import com.ftd.fraud_transaction_detector.aml.feature.domain.HistoricalTransaction;
import com.ftd.fraud_transaction_detector.aml.profile.domain.TrustedCustomerProfile;
import com.ftd.fraud_transaction_detector.transactions.entity.Transaction;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class CustomerProfileRepository {

    private static final int RECENT_STATE_LIMIT = 200;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CustomerProfileRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<TrustedCustomerProfile> findTrusted(String customerId) {
        String sql = """
                SELECT customer_id, trusted_transaction_count, trusted_avg_amount,
                       trusted_variance_amount, trusted_std_amount, trusted_max_amount,
                       trusted_min_amount, usual_start_hour, usual_end_hour,
                       dominant_channel, dominant_location_code, profile_confidence,
                       profile_status, last_learned_at
                FROM dbo.aml_customer_trusted_profile
                WHERE customer_id = :customerId
                """;
        return jdbcTemplate.query(sql, Map.of("customerId", customerId), (resultSet, rowNumber) ->
                new TrustedCustomerProfile(
                        resultSet.getString("customer_id"),
                        TrustedCustomerProfile.snapshot(
                                resultSet.getLong("trusted_transaction_count"),
                                nullableDouble(resultSet, "trusted_avg_amount"),
                                nullableDouble(resultSet, "trusted_variance_amount"),
                                nullableDouble(resultSet, "trusted_std_amount"),
                                nullableDouble(resultSet, "trusted_max_amount"),
                                nullableDouble(resultSet, "trusted_min_amount"),
                                nullableInteger(resultSet, "usual_start_hour"),
                                nullableInteger(resultSet, "usual_end_hour"),
                                resultSet.getString("dominant_channel"),
                                resultSet.getString("dominant_location_code"),
                                resultSet.getDouble("profile_confidence"),
                                resultSet.getString("profile_status")
                        ),
                        nullableDateTime(resultSet.getTimestamp("last_learned_at"))
                )
        ).stream().findFirst();
    }

    public List<HistoricalTransaction> findRecentBefore(
            String customerId,
            LocalDateTime transactionDate,
            int limit
    ) {
        String sql = """
                SELECT TOP (:limit)
                       transaction_id, transaction_amount, transaction_date,
                       transaction_type, channel, location_code,
                       beneficiary_id, device_id, trusted_flag
                FROM dbo.aml_customer_recent_transactions
                WHERE customer_id = :customerId
                  AND transaction_date < :transactionDate
                ORDER BY transaction_date DESC, transaction_id DESC
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("customerId", customerId)
                .addValue("transactionDate", transactionDate)
                .addValue("limit", limit);
        return jdbcTemplate.query(sql, parameters, (resultSet, rowNumber) -> new HistoricalTransaction(
                resultSet.getString("transaction_id"),
                resultSet.getBigDecimal("transaction_amount"),
                resultSet.getTimestamp("transaction_date").toLocalDateTime(),
                resultSet.getString("transaction_type"),
                resultSet.getString("channel"),
                resultSet.getString("location_code"),
                resultSet.getString("beneficiary_id"),
                resultSet.getString("device_id"),
                resultSet.getBoolean("trusted_flag")
        ));
    }

    public void updateObserved(Transaction transaction) {
        String sql = """
                UPDATE dbo.aml_customer_observed_profile WITH (UPDLOCK, SERIALIZABLE)
                SET observed_std_amount = SQRT(
                        ((total_transaction_count * POWER(COALESCE(observed_std_amount, 0), 2))
                        + (:amount - COALESCE(observed_avg_amount, :amount))
                        * (:amount - (COALESCE(observed_avg_amount, :amount)
                        + (:amount - COALESCE(observed_avg_amount, :amount)) / (total_transaction_count + 1))))
                        / (total_transaction_count + 1)
                    ),
                    observed_avg_amount = COALESCE(observed_avg_amount, :amount)
                        + (:amount - COALESCE(observed_avg_amount, :amount)) / (total_transaction_count + 1),
                    observed_max_amount = CASE
                        WHEN observed_max_amount IS NULL OR :amount > observed_max_amount THEN :amount
                        ELSE observed_max_amount END,
                    total_transaction_count = total_transaction_count + 1,
                    total_transaction_amount = total_transaction_amount + :amount,
                    last_transaction_id = :transactionId,
                    last_transaction_date = :transactionDate,
                    last_transaction_amount = :amount,
                    last_location_code = :location,
                    last_channel = :channel,
                    updated_at = SYSUTCDATETIME()
                WHERE customer_id = :customerId
                """;
        MapSqlParameterSource parameters = transactionParameters(transaction);
        int updated = jdbcTemplate.update(sql, parameters);
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO dbo.aml_customer_observed_profile (
                        customer_id, total_transaction_count, total_transaction_amount,
                        last_transaction_id, last_transaction_date, last_transaction_amount,
                        last_location_code, last_channel, observed_avg_amount,
                        observed_std_amount, observed_max_amount, updated_at
                    ) VALUES (
                        :customerId, 1, :amount, :transactionId, :transactionDate, :amount,
                        :location, :channel, :amount, 0, :amount, SYSUTCDATETIME()
                    )
                    """, parameters);
        }
    }

    public void updateTrusted(Transaction transaction) {
        String sql = """
                UPDATE dbo.aml_customer_trusted_profile WITH (UPDLOCK, SERIALIZABLE)
                SET trusted_variance_amount = (
                        (trusted_transaction_count * COALESCE(trusted_variance_amount, 0))
                        + (:amount - COALESCE(trusted_avg_amount, :amount))
                        * (:amount - (COALESCE(trusted_avg_amount, :amount)
                        + (:amount - COALESCE(trusted_avg_amount, :amount)) / (trusted_transaction_count + 1))))
                        / (trusted_transaction_count + 1),
                    trusted_std_amount = SQRT(
                        ((trusted_transaction_count * COALESCE(trusted_variance_amount, 0))
                        + (:amount - COALESCE(trusted_avg_amount, :amount))
                        * (:amount - (COALESCE(trusted_avg_amount, :amount)
                        + (:amount - COALESCE(trusted_avg_amount, :amount)) / (trusted_transaction_count + 1))))
                        / (trusted_transaction_count + 1)
                    ),
                    trusted_avg_amount = COALESCE(trusted_avg_amount, :amount)
                        + (:amount - COALESCE(trusted_avg_amount, :amount)) / (trusted_transaction_count + 1),
                    trusted_max_amount = CASE
                        WHEN trusted_max_amount IS NULL OR :amount > trusted_max_amount THEN :amount
                        ELSE trusted_max_amount END,
                    trusted_min_amount = CASE
                        WHEN trusted_min_amount IS NULL OR :amount < trusted_min_amount THEN :amount
                        ELSE trusted_min_amount END,
                    usual_start_hour = CASE
                        WHEN usual_start_hour IS NULL OR :transactionHour < usual_start_hour THEN :transactionHour
                        ELSE usual_start_hour END,
                    usual_end_hour = CASE
                        WHEN usual_end_hour IS NULL OR :transactionHour > usual_end_hour THEN :transactionHour
                        ELSE usual_end_hour END,
                    dominant_channel = COALESCE(dominant_channel, :channel),
                    dominant_location_code = COALESCE(dominant_location_code, :location),
                    trusted_transaction_count = trusted_transaction_count + 1,
                    profile_confidence = CASE
                        WHEN trusted_transaction_count + 1 >= 30 THEN 1.0
                        ELSE (trusted_transaction_count + 1) / 30.0 END,
                    profile_status = CASE
                        WHEN trusted_transaction_count + 1 < 10 THEN 'LOW_CONFIDENCE'
                        WHEN trusted_transaction_count + 1 < 30 THEN 'DEVELOPING'
                        ELSE 'ESTABLISHED' END,
                    last_learned_transaction_id = :transactionId,
                    last_learned_at = :transactionDate,
                    updated_at = SYSUTCDATETIME()
                WHERE customer_id = :customerId
                  AND profile_status NOT IN ('FROZEN', 'UNDER_REVIEW')
                """;
        MapSqlParameterSource parameters = transactionParameters(transaction)
                .addValue("transactionHour", transaction.getTransactionDate().getHour());
        int updated = jdbcTemplate.update(sql, parameters);
        if (updated == 0 && findTrusted(transaction.getCustomerId()).isEmpty()) {
            jdbcTemplate.update("""
                    INSERT INTO dbo.aml_customer_trusted_profile (
                        customer_id, trusted_transaction_count, trusted_avg_amount,
                        trusted_variance_amount, trusted_std_amount, trusted_max_amount,
                        trusted_min_amount, usual_start_hour, usual_end_hour,
                        dominant_channel, dominant_location_code, profile_confidence,
                        profile_status, last_learned_transaction_id, last_learned_at, updated_at
                    ) VALUES (
                        :customerId, 1, :amount, 0, 0, :amount, :amount,
                        :transactionHour, :transactionHour, :channel, :location,
                        1.0 / 30.0, 'LOW_CONFIDENCE', :transactionId, :transactionDate,
                        SYSUTCDATETIME()
                    )
                    """, parameters);
        }
    }

    public void saveRecent(Transaction transaction, boolean trusted, String riskLevel) {
        MapSqlParameterSource parameters = transactionParameters(transaction)
                .addValue("trusted", trusted)
                .addValue("riskLevel", riskLevel)
                .addValue("limit", RECENT_STATE_LIMIT);
        jdbcTemplate.update("""
                INSERT INTO dbo.aml_customer_recent_transactions (
                    customer_id, transaction_id, transaction_date, transaction_amount,
                    transaction_type, channel, location_code, beneficiary_id, device_id,
                    trusted_flag, anomaly_risk_level, inserted_at
                ) VALUES (
                    :customerId, :transactionId, :transactionDate, :amount,
                    :transactionType, :channel, :location, NULL, NULL,
                    :trusted, :riskLevel, SYSUTCDATETIME()
                )
                """, parameters);
        jdbcTemplate.update("""
                DELETE FROM dbo.aml_customer_recent_transactions
                WHERE customer_id = :customerId
                  AND transaction_id IN (
                      SELECT transaction_id
                      FROM (
                          SELECT transaction_id,
                                 ROW_NUMBER() OVER (
                                     ORDER BY transaction_date DESC, transaction_id DESC
                                 ) AS row_number
                          FROM dbo.aml_customer_recent_transactions
                          WHERE customer_id = :customerId
                      ) ranked
                      WHERE ranked.row_number > :limit
                  )
                """, parameters);
    }

    public void markRecentTrusted(String transactionId) {
        jdbcTemplate.update("""
                UPDATE dbo.aml_customer_recent_transactions
                SET trusted_flag = 1
                WHERE transaction_id = :transactionId
                  AND trusted_flag = 0
                """, Map.of("transactionId", transactionId));
    }

    private MapSqlParameterSource transactionParameters(Transaction transaction) {
        return new MapSqlParameterSource()
                .addValue("customerId", transaction.getCustomerId())
                .addValue("transactionId", transaction.getTransactionId())
                .addValue("transactionDate", transaction.getTransactionDate())
                .addValue("amount", transaction.getTransactionAmount())
                .addValue("transactionType", transaction.getTransactionType())
                .addValue("channel", transaction.getChannel())
                .addValue("location", transaction.getLocation());
    }

    private static Double nullableDouble(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Integer nullableInteger(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private static LocalDateTime nullableDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
