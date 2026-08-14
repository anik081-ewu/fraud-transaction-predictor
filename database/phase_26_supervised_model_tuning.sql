USE [fraud-transaction-detector];
GO

DECLARE @defaults TABLE (
    config_key VARCHAR(150),
    config_value VARCHAR(200),
    value_type VARCHAR(30),
    description VARCHAR(500)
);

INSERT INTO @defaults VALUES
('ml.xgboost.enabled', 'true', 'BOOLEAN', 'Generate XGBoost candidates during supervised training.'),
('ml.xgboost.n_estimators', '300', 'INTEGER', 'Number of XGBoost boosting trees.'),
('ml.xgboost.max_depth', '6', 'INTEGER', 'Maximum XGBoost tree depth.'),
('ml.xgboost.learning_rate', '0.05', 'DECIMAL', 'XGBoost learning rate.'),
('ml.xgboost.subsample', '0.8', 'DECIMAL', 'XGBoost row sample fraction.'),
('ml.xgboost.colsample_bytree', '0.8', 'DECIMAL', 'XGBoost feature sample fraction.'),
('ml.random_forest.enabled', 'true', 'BOOLEAN', 'Generate Random Forest candidates during supervised training.'),
('ml.random_forest.n_estimators', '300', 'INTEGER', 'Number of Random Forest trees.'),
('ml.random_forest.max_depth', '12', 'INTEGER', 'Maximum Random Forest tree depth.'),
('ml.random_forest.min_samples_leaf', '2', 'INTEGER', 'Minimum rows in a Random Forest terminal leaf.'),
('ml.logistic_regression.enabled', 'true', 'BOOLEAN', 'Generate Logistic Regression candidates during supervised training.'),
('ml.logistic_regression.c', '1.0', 'DECIMAL', 'Logistic Regression inverse regularization strength.'),
('ml.logistic_regression.max_iter', '1000', 'INTEGER', 'Maximum Logistic Regression optimization iterations.');

INSERT INTO dbo.app_config (config_key, config_value, value_type, description, updated_at)
SELECT d.config_key, d.config_value, d.value_type, d.description, SYSUTCDATETIME()
FROM @defaults d
WHERE NOT EXISTS (SELECT 1 FROM dbo.app_config c WHERE c.config_key = d.config_key);
GO

SELECT config_key, config_value, value_type
FROM dbo.app_config
WHERE config_key LIKE 'ml.xgboost.%'
   OR config_key LIKE 'ml.random_forest.%'
   OR config_key LIKE 'ml.logistic_regression.%'
ORDER BY config_key;
GO
