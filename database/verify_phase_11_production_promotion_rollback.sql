USE [fraud-transaction-detector];
GO

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'dbo'
  AND table_name IN ('aml_active_models', 'aml_model_deployments')
ORDER BY table_name;
GO

SELECT model_type, model_segment, active_model_version, previous_model_version,
       pointer_version, activated_by, activated_at
FROM dbo.aml_active_models
ORDER BY model_type, model_segment;
GO

SELECT TOP (50) deployment_id, action_id, deployment_action, model_type, model_segment,
       previous_model_version, activated_model_version, reason, performed_by, performed_at
FROM dbo.aml_model_deployments
ORDER BY performed_at DESC;
GO
