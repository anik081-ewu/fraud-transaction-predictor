USE [fraud-transaction-detector];
GO

SELECT config_key, config_value, value_type, description
FROM dbo.app_config
WHERE config_key LIKE 'aml.hst.%'
ORDER BY config_key;
GO

SELECT TOP (20)
    model_version, model_type, model_segment, feature_version,
    status, anomaly_rate, validation_row_count, alert_count,
    average_score, score_p95, score_p99, artifact_path, created_at
FROM dbo.aml_model_registry
WHERE model_type = 'HALF_SPACE_TREES'
ORDER BY created_at DESC;
GO
