USE [fraud-transaction-detector];
GO

IF NOT EXISTS (
    SELECT 1 FROM dbo.app_config WHERE config_key = 'system.learning_mode'
)
BEGIN
    INSERT INTO dbo.app_config (
        config_key, config_value, value_type, description, updated_at
    ) VALUES (
        'system.learning_mode',
        'UNSUPERVISED',
        'SELECT',
        'Global learning mode. Allowed values: UNSUPERVISED or SUPERVISED.',
        SYSUTCDATETIME()
    );
END;
GO

SELECT config_key, config_value, value_type, description
FROM dbo.app_config
WHERE config_key = 'system.learning_mode';
GO
