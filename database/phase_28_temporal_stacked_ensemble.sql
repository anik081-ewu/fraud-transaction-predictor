USE [fraud-transaction-detector];
GO

IF OBJECT_ID('dbo.app_config', 'U') IS NULL
    THROW 51280, 'app_config is missing.', 1;
GO

MERGE dbo.app_config AS target
USING (VALUES
    ('ml.stacked_ensemble.enabled', 'true', 'BOOLEAN', 'Train the leakage-safe temporal supervised stack.'),
    ('ml.supervised.stacking_protocol', 'CALIBRATION_SPLIT', 'TEXT', 'Fit stack weights on the first calibration half and its threshold on the second half.')
) AS source(config_key, config_value, value_type, description)
ON target.config_key = source.config_key
WHEN NOT MATCHED THEN
    INSERT (config_key, config_value, value_type, description, updated_at)
    VALUES (source.config_key, source.config_value, source.value_type, source.description, SYSUTCDATETIME());
GO

SELECT config_key, config_value, value_type
FROM dbo.app_config
WHERE config_key LIKE 'ml.stacked_ensemble.%'
   OR config_key = 'ml.supervised.stacking_protocol'
ORDER BY config_key;
GO
