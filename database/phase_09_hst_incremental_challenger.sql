USE [fraud-transaction-detector];
GO

IF OBJECT_ID('dbo.aml_training_runs', 'U') IS NULL OR OBJECT_ID('dbo.aml_model_registry', 'U') IS NULL
    THROW 51090, 'Run phases 01 through 08 before phase 09.', 1;
GO

MERGE dbo.app_config AS target
USING (VALUES
    ('aml.hst.enabled', 'true', 'BOOLEAN', 'Score the latest compatible HST candidate silently without changing production decisions.'),
    ('aml.hst.n_trees', '25', 'INTEGER', 'Number of Half-Space Trees.'),
    ('aml.hst.height', '8', 'INTEGER', 'Height of each Half-Space Tree.'),
    ('aml.hst.window_size', '250', 'INTEGER', 'Streaming mass-update window size.'),
    ('aml.hst.threshold_quantile', '0.99', 'DECIMAL', 'Candidate anomaly threshold quantile.'),
    ('aml.hst.parquet_batch_size', '65536', 'INTEGER', 'Maximum Parquet records decoded per Python batch.'),
    ('aml.hst.seed', '42', 'INTEGER', 'Deterministic HST random seed.')
) AS source(config_key, config_value, value_type, description)
ON target.config_key = source.config_key
WHEN NOT MATCHED THEN
    INSERT (config_key, config_value, value_type, description, updated_at)
    VALUES (source.config_key, source.config_value, source.value_type, source.description, SYSUTCDATETIME());
GO

PRINT 'Phase 09 HST incremental challenger configuration completed.';
GO
