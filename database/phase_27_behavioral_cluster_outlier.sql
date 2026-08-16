USE [fraud-transaction-detector];
GO

IF OBJECT_ID('dbo.app_config', 'U') IS NULL
    THROW 51270, 'app_config is missing.', 1;
GO

MERGE dbo.app_config AS target
USING (VALUES
    ('aml.cluster_outlier.enabled', 'true', 'BOOLEAN', 'Generate Behavioral Cluster Outlier candidates during unsupervised training.'),
    ('ml.cluster_outlier.n_clusters', '16', 'INTEGER', 'Number of broad transaction-behaviour groups.'),
    ('ml.cluster_outlier.batch_size', '2048', 'INTEGER', 'Rows processed per MiniBatch K-Means update.'),
    ('ml.cluster_outlier.contamination', '0.01', 'DECIMAL', 'Expected cluster-conditional anomaly fraction.'),
    ('aml.research.cluster_outlier_max_training_rows', '100000', 'INTEGER', 'Maximum rows used by Behavioral Cluster Outlier in each growth partition.')
) AS source(config_key, config_value, value_type, description)
ON target.config_key = source.config_key
WHEN NOT MATCHED THEN
    INSERT (config_key, config_value, value_type, description, updated_at)
    VALUES (source.config_key, source.config_value, source.value_type, source.description, SYSUTCDATETIME());
GO

UPDATE dbo.app_config
SET config_value = REPLACE(config_value, 'LOCAL_OUTLIER_FACTOR', 'BEHAVIORAL_CLUSTER_OUTLIER'),
    updated_at = SYSUTCDATETIME()
WHERE config_key = 'aml.risk.ml_model_allocations_json'
  AND config_value LIKE '%LOCAL_OUTLIER_FACTOR%';
GO

DELETE FROM dbo.app_config
WHERE config_key IN (
    'aml.lof.enabled',
    'ml.lof.n_neighbors',
    'ml.lof.contamination'
);
GO

SELECT config_key, config_value, value_type
FROM dbo.app_config
WHERE config_key LIKE '%cluster_outlier%'
   OR config_key = 'aml.risk.ml_model_allocations_json'
ORDER BY config_key;
GO
