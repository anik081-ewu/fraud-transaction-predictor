USE [fraud-transaction-detector];
GO

IF OBJECT_ID('dbo.aml_training_runs', 'U') IS NULL
    THROW 51000, 'Run phase_01_aml_schema_foundation.sql before phase 08.', 1;
GO

IF OBJECT_ID('dbo.aml_model_registry', 'U') IS NULL
    THROW 51000, 'Run phase_01_aml_schema_foundation.sql before phase 08.', 1;
GO

IF COL_LENGTH('dbo.aml_model_registry', 'dataset_checksum') IS NULL
    ALTER TABLE dbo.aml_model_registry ADD dataset_checksum VARCHAR(200) NULL;
GO
IF COL_LENGTH('dbo.aml_model_registry', 'base_model_version') IS NULL
    ALTER TABLE dbo.aml_model_registry ADD base_model_version VARCHAR(100) NULL;
GO
IF COL_LENGTH('dbo.aml_model_registry', 'feature_schema_checksum') IS NULL
    ALTER TABLE dbo.aml_model_registry ADD feature_schema_checksum VARCHAR(200) NULL;
GO
IF COL_LENGTH('dbo.aml_model_registry', 'parameters_json') IS NULL
    ALTER TABLE dbo.aml_model_registry ADD parameters_json NVARCHAR(MAX) NULL;
GO
IF COL_LENGTH('dbo.aml_model_registry', 'metrics_json') IS NULL
    ALTER TABLE dbo.aml_model_registry ADD metrics_json NVARCHAR(MAX) NULL;
GO
IF COL_LENGTH('dbo.aml_model_registry', 'artifact_size_bytes') IS NULL
    ALTER TABLE dbo.aml_model_registry ADD artifact_size_bytes BIGINT NULL;
GO
IF COL_LENGTH('dbo.aml_model_registry', 'registered_by') IS NULL
    ALTER TABLE dbo.aml_model_registry ADD registered_by VARCHAR(100) NULL;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.aml_model_registry')
      AND name = 'UX_aml_registry_training_run'
)
BEGIN
    IF EXISTS (
        SELECT training_run_id
        FROM dbo.aml_model_registry
        GROUP BY training_run_id
        HAVING COUNT(*) > 1
    )
        THROW 51000, 'Duplicate model-registry rows exist for a training run.', 1;

    CREATE UNIQUE INDEX UX_aml_registry_training_run
        ON dbo.aml_model_registry(training_run_id);
END;
GO

MERGE dbo.app_config AS target
USING (VALUES
    ('aml.model.artifact_base_path', 'outputs/model-artifacts', 'STRING',
     'Base directory containing immutable candidate model bundles.')
) AS source(config_key, config_value, value_type, description)
ON target.config_key = source.config_key
WHEN NOT MATCHED THEN
    INSERT (config_key, config_value, value_type, description, updated_at)
    VALUES (source.config_key, source.config_value, source.value_type, source.description, SYSUTCDATETIME());
GO

PRINT 'Phase 08 training-run and model-registry workflow migration completed.';
GO
