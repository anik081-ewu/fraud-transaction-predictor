USE [fraud-transaction-detector];
GO

DELETE FROM dbo.app_config
WHERE config_key LIKE 'aml.hst.%'
   OR config_key LIKE 'aml.online_ocsvm.%';
GO

SELECT config_key, config_value
FROM dbo.app_config
WHERE config_key IN (
    'aml.isolation_forest.enabled',
    'aml.autoencoder.enabled',
    'aml.lof.enabled',
    'ml.lof.n_neighbors',
    'ml.lof.contamination'
)
ORDER BY config_key;
GO
