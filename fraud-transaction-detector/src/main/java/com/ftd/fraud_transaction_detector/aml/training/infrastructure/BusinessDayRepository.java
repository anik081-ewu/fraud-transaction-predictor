package com.ftd.fraud_transaction_detector.aml.training.infrastructure;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Repository
public class BusinessDayRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BusinessDayRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void close(LocalDate businessDate, String closedBy) {
        jdbcTemplate.update("""
                MERGE dbo.aml_business_days WITH (HOLDLOCK) AS target
                USING (SELECT :businessDate AS business_date) AS source
                ON target.business_date = source.business_date
                WHEN MATCHED THEN UPDATE SET
                    status = 'CLOSED', closed_at = SYSUTCDATETIME(),
                    closed_by = :closedBy, updated_at = SYSUTCDATETIME()
                WHEN NOT MATCHED THEN INSERT (
                    business_date, status, closed_at, closed_by, updated_at
                ) VALUES (
                    :businessDate, 'CLOSED', SYSUTCDATETIME(), :closedBy, SYSUTCDATETIME()
                );
                """, new MapSqlParameterSource()
                .addValue("businessDate", businessDate)
                .addValue("closedBy", closedBy));
    }

    public int closeRange(LocalDate fromDate, LocalDate toDate, String closedBy) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("fromDate", fromDate)
                .addValue("toDate", toDate)
                .addValue("closedBy", closedBy);
        Integer dateCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM (
                    SELECT DISTINCT COALESCE(txn.business_date, CAST(txn.transaction_date AS DATE)) AS business_date
                    FROM dbo.transactions txn
                    WHERE COALESCE(txn.business_date, CAST(txn.transaction_date AS DATE))
                        BETWEEN :fromDate AND :toDate
                ) transaction_dates
                """, parameters, Integer.class);

        jdbcTemplate.update("""
                MERGE dbo.aml_business_days WITH (HOLDLOCK) AS target
                USING (
                    SELECT DISTINCT COALESCE(txn.business_date, CAST(txn.transaction_date AS DATE)) AS business_date
                    FROM dbo.transactions txn
                    WHERE COALESCE(txn.business_date, CAST(txn.transaction_date AS DATE))
                        BETWEEN :fromDate AND :toDate
                ) AS source
                ON target.business_date = source.business_date
                WHEN MATCHED THEN UPDATE SET
                    status = 'CLOSED', closed_at = SYSUTCDATETIME(),
                    closed_by = :closedBy, updated_at = SYSUTCDATETIME()
                WHEN NOT MATCHED THEN INSERT (
                    business_date, status, closed_at, closed_by, updated_at
                ) VALUES (
                    source.business_date, 'CLOSED', SYSUTCDATETIME(), :closedBy, SYSUTCDATETIME()
                );
                """, parameters);
        return dateCount == null ? 0 : dateCount;
    }

    public List<LocalDate> findUnclosedTransactionDates(LocalDate fromDate, LocalDate toDate) {
        return jdbcTemplate.query("""
                SELECT DISTINCT COALESCE(txn.business_date, CAST(txn.transaction_date AS DATE)) AS business_date
                FROM dbo.transactions txn
                LEFT JOIN dbo.aml_business_days business_day
                    ON business_day.business_date = COALESCE(txn.business_date, CAST(txn.transaction_date AS DATE))
                   AND business_day.status = 'CLOSED'
                WHERE COALESCE(txn.business_date, CAST(txn.transaction_date AS DATE)) BETWEEN :fromDate AND :toDate
                  AND business_day.business_date IS NULL
                ORDER BY business_date
                """, Map.of("fromDate", fromDate, "toDate", toDate),
                (resultSet, rowNumber) -> resultSet.getDate(1).toLocalDate());
    }
}
