package com.ftd.fraud_transaction_detector.aml.feature.infrastructure;

import com.ftd.fraud_transaction_detector.aml.feature.domain.TerminalRiskContext;
import com.ftd.fraud_transaction_detector.aml.feature.domain.TerminalWindowStatistics;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

@Repository
public class TerminalRiskContextRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TerminalRiskContextRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public TerminalRiskContext load(
            String terminal,
            LocalDateTime asOf,
            int delayDays,
            double smoothingStrength,
            int minimumTransactions,
            boolean enabled
    ) {
        if (!enabled || terminal == null || terminal.isBlank()) return TerminalRiskContext.disabled();
        LocalDateTime labelCutoff = asOf.minusDays(delayDays);
        var parameters = new MapSqlParameterSource()
                .addValue("terminal", terminal.trim())
                .addValue("asOf", asOf)
                .addValue("labelCutoff", labelCutoff);
        return jdbcTemplate.queryForObject("""
                WITH terminal_stats AS (
                    SELECT
                        tx_1d = COALESCE(SUM(CASE WHEN transaction_date >= DATEADD(day, -1, :asOf) AND transaction_date < :asOf THEN 1 ELSE 0 END), 0),
                        avg_1d = COALESCE(AVG(CASE WHEN transaction_date >= DATEADD(day, -1, :asOf) AND transaction_date < :asOf THEN CAST(transaction_amount AS float) END), 0),
                        labels_1d = COALESCE(SUM(CASE WHEN transaction_date >= DATEADD(day, -1, :labelCutoff) AND transaction_date < :labelCutoff AND fraud_label IS NOT NULL AND UPPER(COALESCE(label_source, '')) <> 'AUTO_NO_CASE' THEN 1 ELSE 0 END), 0),
                        fraud_1d = COALESCE(SUM(CASE WHEN transaction_date >= DATEADD(day, -1, :labelCutoff) AND transaction_date < :labelCutoff AND fraud_label = 1 AND UPPER(COALESCE(label_source, '')) <> 'AUTO_NO_CASE' THEN 1 ELSE 0 END), 0),
                        tx_7d = COALESCE(SUM(CASE WHEN transaction_date >= DATEADD(day, -7, :asOf) AND transaction_date < :asOf THEN 1 ELSE 0 END), 0),
                        avg_7d = COALESCE(AVG(CASE WHEN transaction_date >= DATEADD(day, -7, :asOf) AND transaction_date < :asOf THEN CAST(transaction_amount AS float) END), 0),
                        labels_7d = COALESCE(SUM(CASE WHEN transaction_date >= DATEADD(day, -7, :labelCutoff) AND transaction_date < :labelCutoff AND fraud_label IS NOT NULL AND UPPER(COALESCE(label_source, '')) <> 'AUTO_NO_CASE' THEN 1 ELSE 0 END), 0),
                        fraud_7d = COALESCE(SUM(CASE WHEN transaction_date >= DATEADD(day, -7, :labelCutoff) AND transaction_date < :labelCutoff AND fraud_label = 1 AND UPPER(COALESCE(label_source, '')) <> 'AUTO_NO_CASE' THEN 1 ELSE 0 END), 0),
                        tx_30d = COALESCE(SUM(CASE WHEN transaction_date >= DATEADD(day, -30, :asOf) AND transaction_date < :asOf THEN 1 ELSE 0 END), 0),
                        avg_30d = COALESCE(AVG(CASE WHEN transaction_date >= DATEADD(day, -30, :asOf) AND transaction_date < :asOf THEN CAST(transaction_amount AS float) END), 0),
                        labels_30d = COALESCE(SUM(CASE WHEN transaction_date >= DATEADD(day, -30, :labelCutoff) AND transaction_date < :labelCutoff AND fraud_label IS NOT NULL AND UPPER(COALESCE(label_source, '')) <> 'AUTO_NO_CASE' THEN 1 ELSE 0 END), 0),
                        fraud_30d = COALESCE(SUM(CASE WHEN transaction_date >= DATEADD(day, -30, :labelCutoff) AND transaction_date < :labelCutoff AND fraud_label = 1 AND UPPER(COALESCE(label_source, '')) <> 'AUTO_NO_CASE' THEN 1 ELSE 0 END), 0)
                    FROM dbo.transactions
                    WHERE location = :terminal
                      AND transaction_date >= DATEADD(day, -30, :labelCutoff)
                      AND transaction_date < :asOf
                ), global_stats AS (
                    SELECT
                        confirmed_count = COUNT_BIG(*),
                        fraud_count = COALESCE(SUM(CASE WHEN fraud_label = 1 THEN 1 ELSE 0 END), 0)
                    FROM dbo.transactions
                    WHERE transaction_date >= DATEADD(day, -30, :labelCutoff)
                      AND transaction_date < :labelCutoff
                      AND fraud_label IS NOT NULL
                      AND UPPER(COALESCE(label_source, '')) <> 'AUTO_NO_CASE'
                )
                SELECT terminal_stats.*,
                       global_rate = CASE WHEN global_stats.confirmed_count = 0 THEN 0.0
                                          ELSE CAST(global_stats.fraud_count AS float) / global_stats.confirmed_count END
                FROM terminal_stats CROSS JOIN global_stats
                """, parameters, (resultSet, rowNumber) -> map(
                resultSet, smoothingStrength, minimumTransactions
        ));
    }

    private TerminalRiskContext map(ResultSet resultSet, double smoothingStrength, int minimumTransactions) throws SQLException {
        return new TerminalRiskContext(
                true,
                statistics(resultSet, "1d"),
                statistics(resultSet, "7d"),
                statistics(resultSet, "30d"),
                resultSet.getDouble("global_rate"),
                smoothingStrength,
                minimumTransactions
        );
    }

    private TerminalWindowStatistics statistics(ResultSet resultSet, String suffix) throws SQLException {
        return new TerminalWindowStatistics(
                resultSet.getLong("tx_" + suffix),
                resultSet.getDouble("avg_" + suffix),
                resultSet.getLong("labels_" + suffix),
                resultSet.getLong("fraud_" + suffix)
        );
    }
}
