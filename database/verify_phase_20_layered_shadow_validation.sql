USE [fraud-transaction-detector];
GO

IF OBJECT_ID('dbo.aml_layered_validations', 'U') IS NULL
    THROW 51220, 'aml_layered_validations is missing.', 1;
GO

IF OBJECT_ID('dbo.aml_shadow_scenario_labels', 'U') IS NULL
    THROW 51221, 'aml_shadow_scenario_labels is missing.', 1;
GO

IF COL_LENGTH('dbo.aml_shadow_predictions', 'peer_group_code') IS NULL
    THROW 51222, 'aml_shadow_predictions.peer_group_code is missing.', 1;
GO

IF (
    SELECT COUNT(*) FROM dbo.app_config
    WHERE config_key LIKE 'aml.layered_validation.%'
) < 15
    THROW 51223, 'One or more layered validation gates are missing.', 1;
GO

SELECT config_key, config_value, value_type, description
FROM dbo.app_config
WHERE config_key LIKE 'aml.layered_validation.%'
ORDER BY config_key;
GO

SELECT TOP (100)
    validation_id,
    risk_policy_version,
    peer_group_code,
    window_started_at,
    window_ended_at,
    sample_count,
    observation_days,
    legacy_alert_count,
    layered_alert_count,
    overlap_count,
    validation_status,
    validated_by,
    validated_at
FROM dbo.aml_layered_validations
ORDER BY validated_at DESC;
GO

PRINT 'Phase 20 layered shadow validation verification completed.';
GO
