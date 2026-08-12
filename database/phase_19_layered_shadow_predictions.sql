USE [fraud-transaction-detector];
GO

IF OBJECT_ID('dbo.transactions', 'U') IS NULL OR OBJECT_ID('dbo.app_config', 'U') IS NULL
    THROW 51190, 'Required transaction or application-configuration table is missing.', 1;
GO

IF OBJECT_ID('dbo.aml_shadow_predictions', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.aml_shadow_predictions (
        shadow_prediction_id UNIQUEIDENTIFIER NOT NULL,
        transaction_id VARCHAR(50) NOT NULL,
        account_id VARCHAR(50) NOT NULL,
        peer_group_code VARCHAR(50) NOT NULL,
        feature_version VARCHAR(30) NOT NULL,
        risk_policy_version VARCHAR(50) NOT NULL,
        legacy_risk_level VARCHAR(20) NOT NULL,
        legacy_suspicious BIT NOT NULL,
        legacy_anomaly_votes INT NOT NULL,
        layered_final_risk_score FLOAT NOT NULL,
        layered_risk_level VARCHAR(20) NOT NULL,
        layered_suspicious BIT NOT NULL,
        hard_rule_override BIT NOT NULL,
        customer_behaviour_score FLOAT NOT NULL,
        customer_behaviour_confidence FLOAT NOT NULL,
        peer_behaviour_score FLOAT NOT NULL,
        peer_behaviour_confidence FLOAT NOT NULL,
        hst_score FLOAT NULL,
        hst_model_version VARCHAR(100) NULL,
        online_ocsvm_score FLOAT NULL,
        online_ocsvm_model_version VARCHAR(100) NULL,
        rule_score FLOAT NOT NULL,
        suspicious_changed BIT NOT NULL,
        risk_level_changed BIT NOT NULL,
        alert_overlap BIT NOT NULL,
        component_scores_json NVARCHAR(MAX) NOT NULL,
        triggered_rules_json NVARCHAR(MAX) NOT NULL,
        reason_codes_json NVARCHAR(MAX) NOT NULL,
        layered_result_json NVARCHAR(MAX) NOT NULL,
        evaluated_at DATETIME2 NOT NULL,
        duration_ms BIGINT NOT NULL,
        CONSTRAINT PK_aml_shadow_predictions PRIMARY KEY (shadow_prediction_id),
        CONSTRAINT CK_aml_shadow_final_score CHECK (layered_final_risk_score BETWEEN 0 AND 1),
        CONSTRAINT CK_aml_shadow_customer_score CHECK (customer_behaviour_score BETWEEN 0 AND 1),
        CONSTRAINT CK_aml_shadow_customer_confidence CHECK (customer_behaviour_confidence BETWEEN 0 AND 1),
        CONSTRAINT CK_aml_shadow_peer_score CHECK (peer_behaviour_score BETWEEN 0 AND 1),
        CONSTRAINT CK_aml_shadow_peer_confidence CHECK (peer_behaviour_confidence BETWEEN 0 AND 1),
        CONSTRAINT CK_aml_shadow_hst_score CHECK (hst_score IS NULL OR hst_score BETWEEN 0 AND 1),
        CONSTRAINT CK_aml_shadow_online_ocsvm_score CHECK (
            online_ocsvm_score IS NULL OR online_ocsvm_score BETWEEN 0 AND 1
        ),
        CONSTRAINT CK_aml_shadow_rule_score CHECK (rule_score BETWEEN 0 AND 1),
        CONSTRAINT CK_aml_shadow_legacy_votes CHECK (legacy_anomaly_votes >= 0),
        CONSTRAINT CK_aml_shadow_component_json CHECK (ISJSON(component_scores_json) = 1),
        CONSTRAINT CK_aml_shadow_triggered_rules_json CHECK (ISJSON(triggered_rules_json) = 1),
        CONSTRAINT CK_aml_shadow_reason_codes_json CHECK (ISJSON(reason_codes_json) = 1),
        CONSTRAINT CK_aml_shadow_layered_result_json CHECK (ISJSON(layered_result_json) = 1),
        CONSTRAINT CK_aml_shadow_duration CHECK (duration_ms >= 0)
    );
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.aml_shadow_predictions')
      AND name = 'IX_aml_shadow_transaction'
)
    CREATE INDEX IX_aml_shadow_transaction
        ON dbo.aml_shadow_predictions(transaction_id, evaluated_at DESC);
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.aml_shadow_predictions')
      AND name = 'IX_aml_shadow_evaluated_risk'
)
    CREATE INDEX IX_aml_shadow_evaluated_risk
        ON dbo.aml_shadow_predictions(evaluated_at DESC, layered_risk_level, legacy_risk_level);
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.aml_shadow_predictions')
      AND name = 'IX_aml_shadow_differences'
)
    CREATE INDEX IX_aml_shadow_differences
        ON dbo.aml_shadow_predictions(suspicious_changed, risk_level_changed, evaluated_at DESC);
GO

MERGE dbo.app_config AS target
USING (VALUES
    ('aml.risk.layered_shadow_enabled', 'true', 'BOOLEAN', 'Run layered scoring and persist comparison without changing production outcomes.'),
    ('aml.risk.legacy_comparison_enabled', 'true', 'BOOLEAN', 'Persist legacy-versus-layered shadow comparisons.')
) AS source(config_key, config_value, value_type, description)
ON target.config_key = source.config_key
WHEN NOT MATCHED THEN
    INSERT (config_key, config_value, value_type, description, updated_at)
    VALUES (source.config_key, source.config_value, source.value_type, source.description, SYSUTCDATETIME());
GO

PRINT 'Phase 19 layered shadow prediction persistence completed.';
GO
