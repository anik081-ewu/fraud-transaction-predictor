USE [fraud-transaction-detector];
GO

SELECT config_key, config_value, value_type, description, updated_at
FROM dbo.app_config
WHERE config_key IN ('aml.legacy_api.enabled', 'aml.legacy_api.sunset_at')
ORDER BY config_key;
GO

IF OBJECT_ID('dbo.anomaly_configs', 'U') IS NOT NULL
BEGIN
    SELECT id, config_no, enabled_models_json, suspicious_vote_threshold,
           medium_risk_vote_threshold, high_risk_vote_threshold, is_active, updated_at
    FROM dbo.anomaly_configs
    WHERE is_active = 1
    ORDER BY updated_at DESC;
END;
GO
