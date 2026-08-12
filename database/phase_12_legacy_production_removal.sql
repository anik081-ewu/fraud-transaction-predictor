USE [fraud-transaction-detector];
GO

IF OBJECT_ID('dbo.app_config', 'U') IS NULL
    THROW 51000, 'Table dbo.app_config is required before phase 12.', 1;
GO

MERGE dbo.app_config AS target
USING (VALUES
    ('aml.legacy_api.enabled', 'true', 'BOOLEAN',
     'Temporarily expose deprecated raw-transaction ML endpoints during the migration window.'),
    ('aml.legacy_api.sunset_at', '2026-12-31T23:59:59Z', 'STRING',
     'Final supported timestamp for deprecated raw-transaction ML endpoints.')
) AS source(config_key, config_value, value_type, description)
ON target.config_key = source.config_key
WHEN MATCHED THEN
    UPDATE SET description = source.description, updated_at = SYSUTCDATETIME()
WHEN NOT MATCHED THEN
    INSERT (config_key, config_value, value_type, description, updated_at)
    VALUES (source.config_key, source.config_value, source.value_type, source.description, SYSUTCDATETIME());
GO

IF OBJECT_ID('dbo.anomaly_configs', 'U') IS NOT NULL
BEGIN
    UPDATE dbo.anomaly_configs
    SET enabled_models_json = '["IsolationForest"]',
        suspicious_vote_threshold = 1,
        medium_risk_vote_threshold = 1,
        high_risk_vote_threshold = 1,
        updated_at = SYSUTCDATETIME()
    WHERE is_active = 1;
END;
GO

PRINT 'Phase 12 legacy production-code removal migration completed.';
GO
