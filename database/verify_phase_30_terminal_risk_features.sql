USE [fraud-transaction-detector];
GO

IF COL_LENGTH('dbo.aml_transaction_features', 'terminal_tx_count_1d') IS NULL
    THROW 51310, 'terminal_tx_count_1d is missing.', 1;
IF COL_LENGTH('dbo.aml_transaction_features', 'terminal_fraud_rate_30d') IS NULL
    THROW 51311, 'terminal_fraud_rate_30d is missing.', 1;
IF COL_LENGTH('dbo.aml_transaction_features', 'terminal_risk_available') IS NULL
    THROW 51312, 'terminal_risk_available is missing.', 1;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.transactions')
      AND name = 'IX_transactions_location_transaction_date'
)
    THROW 51313, 'IX_transactions_location_transaction_date is missing.', 1;

IF (SELECT COUNT(*) FROM dbo.app_config WHERE config_key LIKE 'aml.terminal_risk.%') < 4
    THROW 51314, 'Terminal-risk app_config keys are incomplete.', 1;
GO

SELECT feature_version, model_feature_schema, COUNT_BIG(*) AS feature_rows,
       SUM(CASE WHEN terminal_risk_available = 1 THEN 1 ELSE 0 END) AS terminal_risk_available_rows
FROM dbo.aml_transaction_features
GROUP BY feature_version, model_feature_schema
ORDER BY feature_version, model_feature_schema;
GO
