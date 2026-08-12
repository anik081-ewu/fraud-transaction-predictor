USE [fraud-transaction-detector];
GO

IF OBJECT_ID('dbo.app_config', 'U') IS NULL
    THROW 51240, 'app_config is missing.', 1;
GO

MERGE dbo.app_config AS target
USING (VALUES
    ('aml.research.minimum_rows', '200', 'INTEGER', 'Minimum rows required for a growth-analysis partition.'),
    ('aml.research.holdout_fraction', '0.20', 'DECIMAL', 'Newest fraction reserved as chronological holdout in each growth partition.'),
    ('aml.research.maximum_evaluation_rows', '20000', 'INTEGER', 'Maximum chronological holdout rows scored per detector and partition.'),
    ('aml.research.random_seed', '42', 'INTEGER', 'Deterministic random seed for offline growth analysis.'),
    ('aml.research.isolation_forest_n_estimators', '200', 'INTEGER', 'Tree count for the bounded Isolation Forest research baseline.'),
    ('aml.research.isolation_forest_max_training_rows', '100000', 'INTEGER', 'Maximum rows loaded into memory for Isolation Forest research training.'),
    ('aml.research.incremental_pca_max_components', '20', 'INTEGER', 'Maximum reconstruction dimensions retained by Incremental PCA research analysis.')
) AS source(config_key, config_value, value_type, description)
ON target.config_key = source.config_key
WHEN NOT MATCHED THEN
    INSERT (config_key, config_value, value_type, description, updated_at)
    VALUES (source.config_key, source.config_value, source.value_type, source.description, SYSUTCDATETIME());
GO

PRINT 'Phase 24 scalable model-tuning configuration completed successfully.';
GO
