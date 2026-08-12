USE [fraud-transaction-detector];
GO

IF OBJECT_ID('dbo.aml_model_registry', 'U') IS NULL
    THROW 51000, 'Run phase_01_aml_schema_foundation.sql before phase 11.', 1;
GO

IF OBJECT_ID('dbo.aml_model_validations', 'U') IS NULL
    THROW 51000, 'Run phase_10_champion_challenger_validation.sql before phase 11.', 1;
GO

IF OBJECT_ID('dbo.aml_active_models', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.aml_active_models (
        model_type VARCHAR(50) NOT NULL,
        model_segment VARCHAR(50) NULL,
        model_segment_key AS ISNULL(model_segment, 'GLOBAL') PERSISTED,
        active_model_version VARCHAR(100) NOT NULL,
        previous_model_version VARCHAR(100) NULL,
        pointer_version BIGINT NOT NULL
            CONSTRAINT DF_aml_active_models_pointer_version DEFAULT 1,
        activated_by VARCHAR(100) NOT NULL,
        activated_at DATETIME2 NOT NULL
            CONSTRAINT DF_aml_active_models_activated_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_aml_active_models PRIMARY KEY (model_type, model_segment_key),
        CONSTRAINT FK_aml_active_models_active FOREIGN KEY (active_model_version)
            REFERENCES dbo.aml_model_registry(model_version),
        CONSTRAINT FK_aml_active_models_previous FOREIGN KEY (previous_model_version)
            REFERENCES dbo.aml_model_registry(model_version),
        CONSTRAINT CK_aml_active_models_versions CHECK (
            previous_model_version IS NULL OR previous_model_version <> active_model_version
        )
    );
END;
GO

IF OBJECT_ID('dbo.aml_model_deployments', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.aml_model_deployments (
        deployment_id UNIQUEIDENTIFIER NOT NULL
            CONSTRAINT PK_aml_model_deployments PRIMARY KEY,
        action_id UNIQUEIDENTIFIER NOT NULL,
        deployment_action VARCHAR(20) NOT NULL,
        model_type VARCHAR(50) NOT NULL,
        model_segment VARCHAR(50) NULL,
        previous_model_version VARCHAR(100) NULL,
        activated_model_version VARCHAR(100) NOT NULL,
        reason NVARCHAR(500) NOT NULL,
        performed_by VARCHAR(100) NOT NULL,
        performed_at DATETIME2 NOT NULL
            CONSTRAINT DF_aml_model_deployments_performed_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT UX_aml_model_deployments_action_id UNIQUE (action_id),
        CONSTRAINT FK_aml_model_deployments_previous FOREIGN KEY (previous_model_version)
            REFERENCES dbo.aml_model_registry(model_version),
        CONSTRAINT FK_aml_model_deployments_activated FOREIGN KEY (activated_model_version)
            REFERENCES dbo.aml_model_registry(model_version),
        CONSTRAINT CK_aml_model_deployments_action CHECK (
            deployment_action IN ('PROMOTION', 'ROLLBACK')
        )
    );
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.aml_model_deployments')
      AND name = 'IX_aml_model_deployments_scope_time'
)
    CREATE INDEX IX_aml_model_deployments_scope_time
        ON dbo.aml_model_deployments(model_type, model_segment, performed_at DESC);
GO

UPDATE dbo.app_config
SET description = 'Enable Half-Space Trees for silent challenger and promoted active-model scoring.',
    updated_at = SYSUTCDATETIME()
WHERE config_key = 'aml.hst.enabled';
GO

PRINT 'Phase 11 production promotion and rollback migration completed.';
GO
