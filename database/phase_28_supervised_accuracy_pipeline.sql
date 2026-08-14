USE [fraud-transaction-detector];
GO

DECLARE @defaults TABLE (
    config_key VARCHAR(150),
    config_value VARCHAR(200),
    value_type VARCHAR(30),
    description VARCHAR(500)
);

INSERT INTO @defaults VALUES
('ml.xgboost.min_child_weight', '3.0', 'DECIMAL', 'Minimum XGBoost child weight used to reduce minority-class overfitting.'),
('ml.xgboost.reg_alpha', '0.05', 'DECIMAL', 'XGBoost L1 regularization for weak or noisy feature effects.'),
('ml.xgboost.reg_lambda', '2.0', 'DECIMAL', 'XGBoost L2 regularization for stable imbalanced classification.'),
('ml.supervised.threshold_beta', '1.0', 'DECIMAL', 'F-beta used to calibrate each supervised model decision threshold.');

INSERT INTO dbo.app_config (config_key, config_value, value_type, description, updated_at)
SELECT d.config_key, d.config_value, d.value_type, d.description, SYSUTCDATETIME()
FROM @defaults d
WHERE NOT EXISTS (SELECT 1 FROM dbo.app_config c WHERE c.config_key = d.config_key);
GO

UPDATE dbo.app_config
SET config_value = CASE config_key
        WHEN 'ml.xgboost.n_estimators' THEN '500'
        WHEN 'ml.xgboost.max_depth' THEN '4'
        WHEN 'ml.xgboost.learning_rate' THEN '0.03'
        WHEN 'ml.xgboost.subsample' THEN '0.85'
        WHEN 'ml.xgboost.colsample_bytree' THEN '0.85'
        WHEN 'ml.random_forest.n_estimators' THEN '500'
        WHEN 'ml.random_forest.max_depth' THEN '16'
        WHEN 'ml.logistic_regression.c' THEN '0.5'
        WHEN 'ml.logistic_regression.max_iter' THEN '2000'
        ELSE config_value
    END,
    updated_at = SYSUTCDATETIME()
WHERE (config_key = 'ml.xgboost.n_estimators' AND config_value = '300')
   OR (config_key = 'ml.xgboost.max_depth' AND config_value = '6')
   OR (config_key = 'ml.xgboost.learning_rate' AND config_value = '0.05')
   OR (config_key = 'ml.xgboost.subsample' AND config_value = '0.8')
   OR (config_key = 'ml.xgboost.colsample_bytree' AND config_value = '0.8')
   OR (config_key = 'ml.random_forest.n_estimators' AND config_value = '300')
   OR (config_key = 'ml.random_forest.max_depth' AND config_value = '12')
   OR (config_key = 'ml.logistic_regression.c' AND config_value = '1.0')
   OR (config_key = 'ml.logistic_regression.max_iter' AND config_value = '1000');
GO

SELECT config_key, config_value, value_type
FROM dbo.app_config
WHERE config_key LIKE 'ml.xgboost.%'
   OR config_key LIKE 'ml.random_forest.%'
   OR config_key LIKE 'ml.logistic_regression.%'
   OR config_key = 'ml.supervised.threshold_beta'
ORDER BY config_key;
GO
