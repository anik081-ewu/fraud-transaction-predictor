USE [fraud-transaction-detector];
GO

IF OBJECT_ID('dbo.app_config', 'U') IS NULL
    THROW 51310, 'app_config is missing.', 1;
GO

MERGE dbo.app_config AS target
USING (VALUES
    ('ml.extra_trees.enabled', 'true', 'BOOLEAN', 'Generate Extra Trees candidates during supervised training.'),
    ('ml.extra_trees.n_estimators', '400', 'INTEGER', 'Number of highly randomized Extra Trees estimators.'),
    ('ml.extra_trees.min_samples_leaf', '2', 'INTEGER', 'Minimum labelled rows in each Extra Trees terminal leaf.'),
    ('ml.extra_trees.max_features', '0.8', 'DECIMAL', 'Feature fraction considered at each Extra Trees split.')
) AS source(config_key, config_value, value_type, description)
ON target.config_key = source.config_key
WHEN NOT MATCHED THEN
    INSERT (config_key, config_value, value_type, description, updated_at)
    VALUES (source.config_key, source.config_value, source.value_type, source.description, SYSUTCDATETIME());
GO

UPDATE dbo.app_config
SET config_value = REPLACE(config_value, 'LOGISTIC_REGRESSION', 'EXTRA_TREES_CLASSIFIER'),
    updated_at = SYSUTCDATETIME()
WHERE config_key = 'aml.risk.ml_model_allocations_json'
  AND config_value LIKE '%LOGISTIC_REGRESSION%';
GO

DELETE FROM dbo.app_config
WHERE config_key IN (
    'ml.logistic_regression.enabled',
    'ml.logistic_regression.c',
    'ml.logistic_regression.max_iter'
);
GO

SELECT config_key, config_value, value_type
FROM dbo.app_config
WHERE config_key LIKE 'ml.extra_trees.%'
   OR config_key = 'aml.risk.ml_model_allocations_json'
ORDER BY config_key;
GO
