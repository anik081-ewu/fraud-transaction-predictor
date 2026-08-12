USE [fraud-transaction-detector];
GO

DECLARE @required_tables TABLE (table_name SYSNAME NOT NULL);
INSERT INTO @required_tables (table_name) VALUES
    ('aml_transaction_features'),
    ('aml_customer_observed_profile'),
    ('aml_customer_trusted_profile'),
    ('aml_customer_recent_transactions'),
    ('aml_feature_learning_status'),
    ('aml_training_runs'),
    ('aml_model_registry');

SELECT
    table_name,
    CASE WHEN OBJECT_ID('dbo.' + table_name, 'U') IS NOT NULL THEN 'OK' ELSE 'MISSING' END AS verification_status
FROM @required_tables
ORDER BY table_name;
GO

DECLARE @required_columns TABLE (
    table_name SYSNAME NOT NULL,
    column_name SYSNAME NOT NULL
);

INSERT INTO @required_columns (table_name, column_name) VALUES
    ('transactions', 'customer_id'),
    ('transactions', 'business_date'),
    ('transactions', 'processing_status'),
    ('transactions', 'feature_status'),
    ('transactions', 'prediction_status'),
    ('transactions', 'updated_at'),
    ('fraud_prediction_logs', 'feature_version'),
    ('fraud_prediction_logs', 'model_version'),
    ('fraud_prediction_logs', 'final_risk_score'),
    ('fraud_prediction_logs', 'learning_decision'),
    ('fraud_prediction_logs', 'prediction_duration_ms');

SELECT
    table_name,
    column_name,
    CASE WHEN COL_LENGTH('dbo.' + table_name, column_name) IS NOT NULL THEN 'OK' ELSE 'MISSING' END AS verification_status
FROM @required_columns
ORDER BY table_name, column_name;
GO

SELECT
    t.name AS table_name,
    SUM(p.rows) AS row_count
FROM sys.tables t
JOIN sys.partitions p ON p.object_id = t.object_id AND p.index_id IN (0, 1)
WHERE t.name LIKE 'aml[_]%'
GROUP BY t.name
ORDER BY t.name;
GO
