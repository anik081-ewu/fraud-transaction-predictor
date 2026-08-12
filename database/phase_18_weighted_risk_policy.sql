USE [fraud-transaction-detector];
GO

IF OBJECT_ID('dbo.app_config', 'U') IS NULL OR OBJECT_ID('dbo.fraud_prediction_logs', 'U') IS NULL
    THROW 51180, 'Required application configuration or prediction-log table is missing.', 1;
GO

MERGE dbo.app_config AS target
USING (VALUES
    ('aml.risk.policy.version', 'AML_RISK_POLICY_V2', 'STRING', 'Active version identifier for layered weighted risk aggregation.'),
    ('aml.risk.weight.customer_behaviour', '0.20', 'DECIMAL', 'Customer Behaviour Scorer weight.'),
    ('aml.risk.weight.peer_behaviour', '0.15', 'DECIMAL', 'Peer Behaviour Scorer weight.'),
    ('aml.risk.weight.half_space_trees', '0.25', 'DECIMAL', 'Half-Space Trees normalized-score weight.'),
    ('aml.risk.weight.online_ocsvm', '0.15', 'DECIMAL', 'Online One-Class SVM normalized-score weight.'),
    ('aml.risk.weight.rules', '0.25', 'DECIMAL', 'Deterministic AML rules normalized-score weight.'),
    ('aml.risk.threshold.low', '0.40', 'DECIMAL', 'Inclusive LOW risk threshold.'),
    ('aml.risk.threshold.medium', '0.65', 'DECIMAL', 'Inclusive MEDIUM risk threshold and suspicious boundary.'),
    ('aml.risk.threshold.high', '0.80', 'DECIMAL', 'Inclusive HIGH risk threshold.'),
    ('aml.risk.layered_shadow_enabled', 'true', 'BOOLEAN', 'Allow layered scoring to run without changing production outcomes.'),
    ('aml.risk.legacy_comparison_enabled', 'true', 'BOOLEAN', 'Retain legacy production scoring for shadow comparison.')
) AS source(config_key, config_value, value_type, description)
ON target.config_key = source.config_key
WHEN NOT MATCHED THEN
    INSERT (config_key, config_value, value_type, description, updated_at)
    VALUES (source.config_key, source.config_value, source.value_type, source.description, SYSUTCDATETIME());
GO

IF COL_LENGTH('dbo.fraud_prediction_logs', 'risk_policy_version') IS NULL
    ALTER TABLE dbo.fraud_prediction_logs ADD risk_policy_version VARCHAR(50) NULL;
GO

IF COL_LENGTH('dbo.fraud_prediction_logs', 'final_risk_score') IS NULL
    ALTER TABLE dbo.fraud_prediction_logs ADD final_risk_score FLOAT NULL;
GO

PRINT 'Phase 18 weighted risk policy configuration completed.';
GO
