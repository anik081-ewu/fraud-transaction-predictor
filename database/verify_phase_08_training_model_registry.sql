USE [fraud-transaction-detector];
GO

SELECT TOP (20)
    training_run_id, training_type, model_type, model_segment,
    status, dataset_checksum, base_model_version, candidate_model_version,
    exported_row_count, learned_row_count, created_at, completed_at
FROM dbo.aml_training_runs
ORDER BY created_at DESC;
GO

SELECT TOP (20)
    model_version, model_type, model_segment, feature_version,
    training_run_id, status, artifact_path, artifact_checksum,
    dataset_checksum, base_model_version, feature_schema_checksum,
    artifact_size_bytes, registered_by, created_at
FROM dbo.aml_model_registry
ORDER BY created_at DESC;
GO

SELECT config_key, config_value, value_type, description
FROM dbo.app_config
WHERE config_key = 'aml.model.artifact_base_path';
GO
