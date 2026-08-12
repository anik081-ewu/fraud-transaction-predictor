USE [fraud-transaction-detector];
GO

SELECT 'observed_profiles' AS metric, COUNT_BIG(*) AS metric_value
FROM dbo.aml_customer_observed_profile
UNION ALL
SELECT 'trusted_profiles', COUNT_BIG(*)
FROM dbo.aml_customer_trusted_profile
UNION ALL
SELECT 'recent_transactions', COUNT_BIG(*)
FROM dbo.aml_customer_recent_transactions;
GO

SELECT COUNT_BIG(*) AS alerted_transactions_in_trusted_recent_state
FROM dbo.aml_customer_recent_transactions recent
WHERE recent.trusted_flag = 1
  AND EXISTS (
      SELECT 1
      FROM dbo.fraud_alerts alert
      WHERE alert.transaction_id = recent.transaction_id
        AND COALESCE(alert.review_status, 'PENDING') <> 'FALSE_POSITIVE'
  );
GO

SELECT TOP (20)
    observed.customer_id,
    observed.total_transaction_count,
    trusted.trusted_transaction_count,
    trusted.profile_status,
    trusted.profile_confidence,
    trusted.last_learned_at
FROM dbo.aml_customer_observed_profile observed
LEFT JOIN dbo.aml_customer_trusted_profile trusted
    ON trusted.customer_id = observed.customer_id
ORDER BY observed.total_transaction_count DESC;
GO
