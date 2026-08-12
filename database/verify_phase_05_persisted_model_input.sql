USE [fraud-transaction-detector];
GO

SELECT
    COUNT_BIG(*) AS total_feature_rows,
    SUM(CASE WHEN model_feature_schema = 'LEGACY_MODEL_INPUT_V1' THEN 1 ELSE 0 END) AS v2_ready_rows,
    SUM(CASE WHEN ISJSON(model_features_json) = 1 THEN 1 ELSE 0 END) AS valid_json_rows
FROM dbo.aml_transaction_features;
GO

SELECT TOP (20)
    transaction_id,
    feature_version,
    model_feature_schema,
    JSON_QUERY(model_features_json) AS model_features_json,
    generated_at
FROM dbo.aml_transaction_features
ORDER BY generated_at DESC, id DESC;
GO
