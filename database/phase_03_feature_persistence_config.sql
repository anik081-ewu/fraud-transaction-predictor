USE [fraud-transaction-detector];
GO

MERGE dbo.app_config AS target
USING (
    VALUES (
        'aml.structuring.reporting_threshold',
        '10000',
        'DECIMAL',
        'Configurable amount threshold used only for below-threshold velocity features; set according to jurisdiction and product.'
    )
) AS source (config_key, config_value, value_type, description)
ON target.config_key = source.config_key
WHEN NOT MATCHED THEN
    INSERT (config_key, config_value, value_type, description, updated_at)
    VALUES (source.config_key, source.config_value, source.value_type, source.description, SYSUTCDATETIME());
GO
