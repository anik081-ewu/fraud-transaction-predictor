USE [fraud-transaction-detector];
GO

SELECT config_key, config_value, value_type, description
FROM dbo.app_config
WHERE config_key LIKE 'aml.validation.%'
ORDER BY config_key;
GO

SELECT TOP (20) *
FROM dbo.aml_model_validations
ORDER BY validated_at DESC;
GO

SELECT TOP (20)
    model_version, model_type, model_segment, status,
    anomaly_rate, validation_row_count, created_at
FROM dbo.aml_model_registry
WHERE model_type = 'HALF_SPACE_TREES'
ORDER BY created_at DESC;
GO
