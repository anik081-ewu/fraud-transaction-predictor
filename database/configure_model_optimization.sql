USE [fraud-transaction-detector];
GO

MERGE dbo.app_config AS target
USING (
    VALUES
        ('ml.optimization.enabled', 'true', 'BOOLEAN', 'Enable model-specific hyperparameter optimization for partition training.'),
        ('ml.optimization.validation_fraction', '0.20', 'DECIMAL', 'Newest chronological fraction reserved for label-free proxy validation.'),
        ('ml.optimization.target_anomaly_rate', '0.05', 'DECIMAL', 'Expected anomaly-rate prior used for candidate calibration.'),
        ('ml.optimization.min_rows', '200', 'INTEGER', 'Minimum partition size required before optimization is applied.'),
        ('ml.optimization.max_training_rows', '5000', 'INTEGER', 'Maximum chronological training rows used during candidate search to bound runtime.')
) AS source (config_key, config_value, value_type, description)
ON target.config_key = source.config_key
WHEN MATCHED THEN
    UPDATE SET
        target.config_value = source.config_value,
        target.value_type = source.value_type,
        target.description = source.description,
        target.updated_at = SYSUTCDATETIME()
WHEN NOT MATCHED THEN
    INSERT (config_key, config_value, value_type, description, updated_at)
    VALUES (source.config_key, source.config_value, source.value_type, source.description, SYSUTCDATETIME());
GO
