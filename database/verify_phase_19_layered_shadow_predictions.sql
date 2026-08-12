USE [fraud-transaction-detector];
GO

IF OBJECT_ID('dbo.aml_shadow_predictions', 'U') IS NULL
    THROW 51200, 'aml_shadow_predictions is missing.', 1;
GO

IF NOT EXISTS (
    SELECT 1
    FROM dbo.app_config
    WHERE config_key = 'aml.risk.layered_shadow_enabled'
)
    THROW 51201, 'Layered shadow configuration is missing.', 1;
GO

IF NOT EXISTS (
    SELECT 1
    FROM dbo.app_config
    WHERE config_key = 'aml.risk.legacy_comparison_enabled'
)
    THROW 51202, 'Legacy comparison configuration is missing.', 1;
GO

SELECT
    COUNT_BIG(*) AS total_shadow_predictions,
    SUM(CASE WHEN suspicious_changed = 1 THEN 1 ELSE 0 END) AS suspicious_decision_changes,
    SUM(CASE WHEN risk_level_changed = 1 THEN 1 ELSE 0 END) AS risk_level_changes,
    SUM(CASE WHEN alert_overlap = 1 THEN 1 ELSE 0 END) AS alert_overlap_count,
    AVG(layered_final_risk_score) AS average_layered_risk_score,
    AVG(CAST(duration_ms AS FLOAT)) AS average_shadow_duration_ms
FROM dbo.aml_shadow_predictions;
GO

SELECT TOP (100)
    transaction_id,
    account_id,
    risk_policy_version,
    legacy_risk_level,
    layered_risk_level,
    legacy_suspicious,
    layered_suspicious,
    suspicious_changed,
    risk_level_changed,
    evaluated_at
FROM dbo.aml_shadow_predictions
ORDER BY evaluated_at DESC;
GO

PRINT 'Phase 19 layered shadow prediction verification completed.';
GO
