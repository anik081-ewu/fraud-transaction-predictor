USE [fraud-transaction-detector];
GO

IF OBJECT_ID('dbo.transactions', 'U') IS NULL
    THROW 51300, 'transactions is missing.', 1;
IF OBJECT_ID('dbo.aml_transaction_features', 'U') IS NULL
    THROW 51301, 'aml_transaction_features is missing.', 1;
IF OBJECT_ID('dbo.app_config', 'U') IS NULL
    THROW 51302, 'app_config is missing.', 1;
GO

IF COL_LENGTH('dbo.aml_transaction_features', 'terminal_tx_count_1d') IS NULL
    ALTER TABLE dbo.aml_transaction_features ADD terminal_tx_count_1d BIGINT NOT NULL CONSTRAINT DF_aml_features_terminal_tx_1d DEFAULT 0;
IF COL_LENGTH('dbo.aml_transaction_features', 'terminal_fraud_count_1d') IS NULL
    ALTER TABLE dbo.aml_transaction_features ADD terminal_fraud_count_1d BIGINT NOT NULL CONSTRAINT DF_aml_features_terminal_fraud_1d DEFAULT 0;
IF COL_LENGTH('dbo.aml_transaction_features', 'terminal_fraud_rate_1d') IS NULL
    ALTER TABLE dbo.aml_transaction_features ADD terminal_fraud_rate_1d FLOAT NOT NULL CONSTRAINT DF_aml_features_terminal_rate_1d DEFAULT 0;
IF COL_LENGTH('dbo.aml_transaction_features', 'terminal_avg_amount_1d') IS NULL
    ALTER TABLE dbo.aml_transaction_features ADD terminal_avg_amount_1d FLOAT NOT NULL CONSTRAINT DF_aml_features_terminal_avg_1d DEFAULT 0;

IF COL_LENGTH('dbo.aml_transaction_features', 'terminal_tx_count_7d') IS NULL
    ALTER TABLE dbo.aml_transaction_features ADD terminal_tx_count_7d BIGINT NOT NULL CONSTRAINT DF_aml_features_terminal_tx_7d DEFAULT 0;
IF COL_LENGTH('dbo.aml_transaction_features', 'terminal_fraud_count_7d') IS NULL
    ALTER TABLE dbo.aml_transaction_features ADD terminal_fraud_count_7d BIGINT NOT NULL CONSTRAINT DF_aml_features_terminal_fraud_7d DEFAULT 0;
IF COL_LENGTH('dbo.aml_transaction_features', 'terminal_fraud_rate_7d') IS NULL
    ALTER TABLE dbo.aml_transaction_features ADD terminal_fraud_rate_7d FLOAT NOT NULL CONSTRAINT DF_aml_features_terminal_rate_7d DEFAULT 0;
IF COL_LENGTH('dbo.aml_transaction_features', 'terminal_avg_amount_7d') IS NULL
    ALTER TABLE dbo.aml_transaction_features ADD terminal_avg_amount_7d FLOAT NOT NULL CONSTRAINT DF_aml_features_terminal_avg_7d DEFAULT 0;

IF COL_LENGTH('dbo.aml_transaction_features', 'terminal_tx_count_30d') IS NULL
    ALTER TABLE dbo.aml_transaction_features ADD terminal_tx_count_30d BIGINT NOT NULL CONSTRAINT DF_aml_features_terminal_tx_30d DEFAULT 0;
IF COL_LENGTH('dbo.aml_transaction_features', 'terminal_fraud_count_30d') IS NULL
    ALTER TABLE dbo.aml_transaction_features ADD terminal_fraud_count_30d BIGINT NOT NULL CONSTRAINT DF_aml_features_terminal_fraud_30d DEFAULT 0;
IF COL_LENGTH('dbo.aml_transaction_features', 'terminal_fraud_rate_30d') IS NULL
    ALTER TABLE dbo.aml_transaction_features ADD terminal_fraud_rate_30d FLOAT NOT NULL CONSTRAINT DF_aml_features_terminal_rate_30d DEFAULT 0;
IF COL_LENGTH('dbo.aml_transaction_features', 'terminal_avg_amount_30d') IS NULL
    ALTER TABLE dbo.aml_transaction_features ADD terminal_avg_amount_30d FLOAT NOT NULL CONSTRAINT DF_aml_features_terminal_avg_30d DEFAULT 0;
IF COL_LENGTH('dbo.aml_transaction_features', 'terminal_risk_available') IS NULL
    ALTER TABLE dbo.aml_transaction_features ADD terminal_risk_available BIT NOT NULL CONSTRAINT DF_aml_features_terminal_available DEFAULT 0;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.transactions')
      AND name = 'IX_transactions_location_transaction_date'
)
BEGIN
    CREATE INDEX IX_transactions_location_transaction_date
        ON dbo.transactions(location, transaction_date)
        INCLUDE (transaction_amount, fraud_label, label_source);
END;
GO

MERGE dbo.app_config AS target
USING (VALUES
    ('aml.terminal_risk.enabled', 'true', 'BOOLEAN', 'Enable point-in-time terminal history features.'),
    ('aml.terminal_risk.delay_days', '7', 'INTEGER', 'Investigation delay applied only to confirmed-label terminal risk features.'),
    ('aml.terminal_risk.smoothing_strength', '20', 'DECIMAL', 'Bayesian prior strength for terminal confirmed-fraud rates.'),
    ('aml.terminal_risk.minimum_transactions', '3', 'INTEGER', 'Minimum 30-day terminal volume required for terminal risk availability.')
) AS source(config_key, config_value, value_type, description)
ON target.config_key = source.config_key
WHEN NOT MATCHED THEN
    INSERT (config_key, config_value, value_type, description, updated_at)
    VALUES (source.config_key, source.config_value, source.value_type, source.description, SYSUTCDATETIME());
GO

SELECT config_key, config_value, value_type
FROM dbo.app_config
WHERE config_key LIKE 'aml.terminal_risk.%'
ORDER BY config_key;
GO
