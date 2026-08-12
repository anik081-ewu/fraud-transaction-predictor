USE [fraud-transaction-detector];
GO

SET XACT_ABORT ON;
GO

IF OBJECT_ID('dbo.aml_customer_observed_profile', 'U') IS NULL
    THROW 51041, 'Run phase_01_aml_schema_foundation.sql before Phase 04.', 1;
GO

/*
 * Legacy transactions are backfilled once. Transactions that already produced
 * an alert are excluded from the trusted baseline but remain in observed state.
 */
;WITH observed AS (
    SELECT
        COALESCE(t.customer_id, t.account_id) AS customer_id,
        COUNT_BIG(*) AS transaction_count,
        SUM(t.transaction_amount) AS transaction_amount,
        AVG(CAST(t.transaction_amount AS FLOAT)) AS average_amount,
        STDEV(CAST(t.transaction_amount AS FLOAT)) AS std_amount,
        MAX(CAST(t.transaction_amount AS FLOAT)) AS maximum_amount
    FROM dbo.transactions t
    GROUP BY COALESCE(t.customer_id, t.account_id)
)
INSERT INTO dbo.aml_customer_observed_profile (
    customer_id, total_transaction_count, total_transaction_amount,
    last_transaction_id, last_transaction_date, last_transaction_amount,
    last_location_code, last_channel, observed_avg_amount,
    observed_std_amount, observed_max_amount, updated_at
)
SELECT
    o.customer_id, o.transaction_count, o.transaction_amount,
    latest.transaction_id, latest.transaction_date, latest.transaction_amount,
    latest.location, latest.channel, o.average_amount,
    COALESCE(o.std_amount, 0), o.maximum_amount, SYSUTCDATETIME()
FROM observed o
CROSS APPLY (
    SELECT TOP (1)
        t.transaction_id, t.transaction_date, t.transaction_amount,
        t.location, t.channel
    FROM dbo.transactions t
    WHERE COALESCE(t.customer_id, t.account_id) = o.customer_id
    ORDER BY t.transaction_date DESC, t.id DESC
) latest
WHERE NOT EXISTS (
    SELECT 1
    FROM dbo.aml_customer_observed_profile p
    WHERE p.customer_id = o.customer_id
);
GO

;WITH trusted_transactions AS (
    SELECT t.*
    FROM dbo.transactions t
    WHERE NOT EXISTS (
        SELECT 1
        FROM dbo.fraud_alerts a
        WHERE a.transaction_id = t.transaction_id
          AND COALESCE(a.review_status, 'PENDING') <> 'FALSE_POSITIVE'
    )
), trusted AS (
    SELECT
        COALESCE(t.customer_id, t.account_id) AS customer_id,
        COUNT_BIG(*) AS transaction_count,
        AVG(CAST(t.transaction_amount AS FLOAT)) AS average_amount,
        VARP(CAST(t.transaction_amount AS FLOAT)) AS variance_amount,
        STDEVP(CAST(t.transaction_amount AS FLOAT)) AS std_amount,
        MAX(CAST(t.transaction_amount AS FLOAT)) AS maximum_amount,
        MIN(CAST(t.transaction_amount AS FLOAT)) AS minimum_amount,
        MIN(DATEPART(HOUR, t.transaction_date)) AS usual_start_hour,
        MAX(DATEPART(HOUR, t.transaction_date)) AS usual_end_hour,
        MAX(t.transaction_date) AS last_learned_at
    FROM trusted_transactions t
    GROUP BY COALESCE(t.customer_id, t.account_id)
)
INSERT INTO dbo.aml_customer_trusted_profile (
    customer_id, trusted_transaction_count, trusted_avg_amount,
    trusted_variance_amount, trusted_std_amount, trusted_max_amount,
    trusted_min_amount, usual_start_hour, usual_end_hour,
    profile_confidence, profile_status, last_learned_transaction_id,
    last_learned_at, updated_at
)
SELECT
    trusted.customer_id,
    trusted.transaction_count,
    trusted.average_amount,
    COALESCE(trusted.variance_amount, 0),
    COALESCE(trusted.std_amount, 0),
    trusted.maximum_amount,
    trusted.minimum_amount,
    trusted.usual_start_hour,
    trusted.usual_end_hour,
    CASE WHEN trusted.transaction_count >= 30 THEN 1.0 ELSE trusted.transaction_count / 30.0 END,
    CASE
        WHEN trusted.transaction_count = 0 THEN 'COLD_START'
        WHEN trusted.transaction_count < 10 THEN 'LOW_CONFIDENCE'
        WHEN trusted.transaction_count < 30 THEN 'DEVELOPING'
        ELSE 'ESTABLISHED'
    END,
    latest.transaction_id,
    trusted.last_learned_at,
    SYSUTCDATETIME()
FROM trusted
CROSS APPLY (
    SELECT TOP (1) t.transaction_id
    FROM trusted_transactions t
    WHERE COALESCE(t.customer_id, t.account_id) = trusted.customer_id
    ORDER BY t.transaction_date DESC, t.id DESC
) latest
WHERE NOT EXISTS (
    SELECT 1
    FROM dbo.aml_customer_trusted_profile p
    WHERE p.customer_id = trusted.customer_id
);
GO

;WITH ranked AS (
    SELECT
        t.*,
        COALESCE(t.customer_id, t.account_id) AS effective_customer_id,
        ROW_NUMBER() OVER (
            PARTITION BY COALESCE(t.customer_id, t.account_id)
            ORDER BY t.transaction_date DESC, t.id DESC
        ) AS row_number
    FROM dbo.transactions t
)
INSERT INTO dbo.aml_customer_recent_transactions (
    customer_id, transaction_id, transaction_date, transaction_amount,
    transaction_type, channel, location_code, beneficiary_id, device_id,
    trusted_flag, anomaly_risk_level, inserted_at
)
SELECT
    ranked.effective_customer_id,
    ranked.transaction_id,
    ranked.transaction_date,
    ranked.transaction_amount,
    ranked.transaction_type,
    ranked.channel,
    ranked.location,
    NULL,
    NULL,
    CASE WHEN EXISTS (
        SELECT 1
        FROM dbo.fraud_alerts a
        WHERE a.transaction_id = ranked.transaction_id
          AND COALESCE(a.review_status, 'PENDING') <> 'FALSE_POSITIVE'
    ) THEN 0 ELSE 1 END,
    (
        SELECT TOP (1) a.risk_level
        FROM dbo.fraud_alerts a
        WHERE a.transaction_id = ranked.transaction_id
        ORDER BY a.created_at DESC
    ),
    SYSUTCDATETIME()
FROM ranked
WHERE ranked.row_number <= 200
  AND NOT EXISTS (
      SELECT 1
      FROM dbo.aml_customer_recent_transactions recent
      WHERE recent.customer_id = ranked.effective_customer_id
        AND recent.transaction_id = ranked.transaction_id
  );
GO

PRINT 'Phase 04 observed/trusted profile backfill completed successfully.';
GO
