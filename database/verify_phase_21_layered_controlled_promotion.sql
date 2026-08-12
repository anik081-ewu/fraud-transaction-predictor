USE [fraud-transaction-detector];
GO

IF OBJECT_ID('dbo.aml_layered_deployment_pointers', 'U') IS NULL
    THROW 51231, 'aml_layered_deployment_pointers is missing.', 1;
GO

IF OBJECT_ID('dbo.aml_layered_deployment_events', 'U') IS NULL
    THROW 51232, 'aml_layered_deployment_events is missing.', 1;
GO

IF NOT EXISTS (
    SELECT 1
    FROM dbo.app_config
    WHERE config_key = 'aml.layered_deployment.max_validation_age_days'
)
    THROW 51233, 'Layered deployment validation-age configuration is missing.', 1;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.aml_layered_deployment_events')
      AND name = 'IX_aml_layered_deployment_segment_date'
)
    THROW 51234, 'Layered deployment audit index is missing.', 1;
GO

SELECT config_key, config_value, value_type, description
FROM dbo.app_config
WHERE config_key = 'aml.layered_deployment.max_validation_age_days';
GO

SELECT
    peer_group_code,
    deployment_mode,
    canary_percentage,
    risk_policy_version,
    hst_model_version,
    online_ocsvm_model_version,
    validation_id,
    pointer_version,
    activated_by,
    activated_at
FROM dbo.aml_layered_deployment_pointers
ORDER BY peer_group_code;
GO

SELECT TOP (100)
    deployment_id,
    action_id,
    deployment_action,
    peer_group_code,
    previous_mode,
    activated_mode,
    previous_canary_percentage,
    activated_canary_percentage,
    risk_policy_version,
    hst_model_version,
    online_ocsvm_model_version,
    validation_id,
    reason,
    performed_by,
    performed_at
FROM dbo.aml_layered_deployment_events
ORDER BY performed_at DESC;
GO

PRINT 'Phase 21 layered controlled promotion verification completed.';
GO
