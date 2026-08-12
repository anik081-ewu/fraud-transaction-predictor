USE [fraud-transaction-detector];
GO

IF OBJECT_ID('dbo.aml_layered_validations', 'U') IS NULL
   OR OBJECT_ID('dbo.aml_model_registry', 'U') IS NULL
   OR OBJECT_ID('dbo.app_config', 'U') IS NULL
    THROW 51230, 'Run all migrations through Phase 20 before Phase 21.', 1;
GO

IF OBJECT_ID('dbo.aml_layered_deployment_pointers', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.aml_layered_deployment_pointers (
        peer_group_code VARCHAR(50) NOT NULL,
        deployment_mode VARCHAR(40) NOT NULL,
        risk_policy_version VARCHAR(50) NOT NULL,
        hst_model_version VARCHAR(100) NOT NULL,
        online_ocsvm_model_version VARCHAR(100) NOT NULL,
        validation_id UNIQUEIDENTIFIER NOT NULL,
        canary_percentage INT NOT NULL,
        pointer_version BIGINT NOT NULL,
        activated_by VARCHAR(100) NOT NULL,
        activated_at DATETIME2 NOT NULL,
        CONSTRAINT PK_aml_layered_deployment_pointers PRIMARY KEY (peer_group_code),
        CONSTRAINT FK_aml_layered_pointer_validation FOREIGN KEY (validation_id)
            REFERENCES dbo.aml_layered_validations(validation_id),
        CONSTRAINT FK_aml_layered_pointer_hst FOREIGN KEY (hst_model_version)
            REFERENCES dbo.aml_model_registry(model_version),
        CONSTRAINT FK_aml_layered_pointer_ocsvm FOREIGN KEY (online_ocsvm_model_version)
            REFERENCES dbo.aml_model_registry(model_version),
        CONSTRAINT CK_aml_layered_pointer_mode CHECK (
            deployment_mode IN ('LAYERED_ACTIVE', 'ISOLATION_FOREST_FALLBACK')
        ),
        CONSTRAINT CK_aml_layered_pointer_canary CHECK (canary_percentage BETWEEN 0 AND 100),
        CONSTRAINT CK_aml_layered_pointer_version CHECK (pointer_version > 0)
    );
END;
GO

IF OBJECT_ID('dbo.aml_layered_deployment_events', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.aml_layered_deployment_events (
        deployment_id UNIQUEIDENTIFIER NOT NULL,
        action_id UNIQUEIDENTIFIER NOT NULL,
        deployment_action VARCHAR(30) NOT NULL,
        peer_group_code VARCHAR(50) NOT NULL,
        previous_mode VARCHAR(40) NULL,
        activated_mode VARCHAR(40) NOT NULL,
        risk_policy_version VARCHAR(50) NOT NULL,
        hst_model_version VARCHAR(100) NOT NULL,
        online_ocsvm_model_version VARCHAR(100) NOT NULL,
        validation_id UNIQUEIDENTIFIER NOT NULL,
        previous_canary_percentage INT NULL,
        activated_canary_percentage INT NOT NULL,
        reason VARCHAR(1000) NOT NULL,
        performed_by VARCHAR(100) NOT NULL,
        performed_at DATETIME2 NOT NULL,
        CONSTRAINT PK_aml_layered_deployment_events PRIMARY KEY (deployment_id),
        CONSTRAINT UQ_aml_layered_deployment_action UNIQUE (action_id),
        CONSTRAINT FK_aml_layered_event_validation FOREIGN KEY (validation_id)
            REFERENCES dbo.aml_layered_validations(validation_id),
        CONSTRAINT CK_aml_layered_event_action CHECK (
            deployment_action IN ('PROMOTION', 'CANARY_EXPANSION', 'ROLLBACK')
        ),
        CONSTRAINT CK_aml_layered_event_mode CHECK (
            activated_mode IN ('LAYERED_ACTIVE', 'ISOLATION_FOREST_FALLBACK')
        ),
        CONSTRAINT CK_aml_layered_event_canary CHECK (activated_canary_percentage BETWEEN 0 AND 100)
    );
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.aml_layered_deployment_events')
      AND name = 'IX_aml_layered_deployment_segment_date'
)
    CREATE INDEX IX_aml_layered_deployment_segment_date
        ON dbo.aml_layered_deployment_events(peer_group_code, performed_at DESC);
GO

MERGE dbo.app_config AS target
USING (VALUES
    ('aml.layered_deployment.max_validation_age_days', '7', 'INTEGER', 'Maximum age of a passing layered validation used for promotion.')
) AS source(config_key, config_value, value_type, description)
ON target.config_key = source.config_key
WHEN NOT MATCHED THEN
    INSERT (config_key, config_value, value_type, description, updated_at)
    VALUES (source.config_key, source.config_value, source.value_type, source.description, SYSUTCDATETIME());
GO

PRINT 'Phase 21 layered controlled promotion migration completed.';
GO
