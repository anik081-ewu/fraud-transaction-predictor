USE [fraud-transaction-detector];
GO

SELECT business_date, status, closed_at, closed_by, updated_at
FROM dbo.aml_business_days
ORDER BY business_date DESC;
GO

SELECT config_key, config_value, value_type
FROM dbo.app_config
WHERE config_key IN (
    'aml.export.base_path',
    'aml.export.chunk_size',
    'aml.export.rows_per_file'
)
ORDER BY config_key;
GO

SELECT TOP (20)
    training_run_id, training_type, feature_version, model_type,
    model_segment, from_business_date, to_business_date,
    requested_row_count, exported_row_count, dataset_path,
    dataset_checksum, status, failure_reason, created_at
FROM dbo.aml_training_runs
ORDER BY created_at DESC;
GO
