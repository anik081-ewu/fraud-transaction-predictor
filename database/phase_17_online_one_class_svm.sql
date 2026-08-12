USE [fraud-transaction-detector];
GO

IF OBJECT_ID('dbo.aml_training_runs', 'U') IS NULL OR OBJECT_ID('dbo.aml_model_registry', 'U') IS NULL
    THROW 51170, 'Run the AML training and model-registry migrations before phase 17.', 1;
GO

MERGE dbo.app_config AS target
USING (VALUES
    ('aml.online_ocsvm.enabled', 'true', 'BOOLEAN', 'Score the latest compatible Online One-Class SVM candidate in shadow mode.'),
    ('aml.online_ocsvm.nu', '0.05', 'DECIMAL', 'Expected anomaly/support-vector fraction for stochastic Online One-Class SVM.'),
    ('aml.online_ocsvm.learning_rate', '0.01', 'DECIMAL', 'Online SGD learning rate.'),
    ('aml.online_ocsvm.intercept_learning_rate', '0.01', 'DECIMAL', 'Online intercept learning rate.'),
    ('aml.online_ocsvm.gamma', '0.5', 'DECIMAL', 'RBF random-feature kernel gamma.'),
    ('aml.online_ocsvm.n_components', '64', 'INTEGER', 'Bounded RBF random-feature component count.'),
    ('aml.online_ocsvm.threshold_quantile', '0.99', 'DECIMAL', 'Raw-score quantile used as the anomaly threshold.'),
    ('aml.online_ocsvm.min_calibration_rows', '200', 'INTEGER', 'Minimum eligible rows required for score calibration.'),
    ('aml.online_ocsvm.parquet_batch_size', '65536', 'INTEGER', 'Maximum Parquet records decoded per Python batch.'),
    ('aml.online_ocsvm.seed', '42', 'INTEGER', 'Deterministic RBF random-feature seed.')
) AS source(config_key, config_value, value_type, description)
ON target.config_key = source.config_key
WHEN NOT MATCHED THEN
    INSERT (config_key, config_value, value_type, description, updated_at)
    VALUES (source.config_key, source.config_value, source.value_type, source.description, SYSUTCDATETIME());
GO

PRINT 'Phase 17 Online One-Class SVM shadow configuration completed.';
GO
