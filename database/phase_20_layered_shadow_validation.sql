USE [fraud-transaction-detector];
GO

IF OBJECT_ID('dbo.aml_shadow_predictions', 'U') IS NULL
   OR OBJECT_ID('dbo.aml_training_runs', 'U') IS NULL
   OR OBJECT_ID('dbo.fraud_alerts', 'U') IS NULL
   OR OBJECT_ID('dbo.app_config', 'U') IS NULL
    THROW 51210, 'Required shadow, training, alert, or configuration tables are missing.', 1;
GO

IF COL_LENGTH('dbo.aml_shadow_predictions', 'peer_group_code') IS NULL
    ALTER TABLE dbo.aml_shadow_predictions ADD peer_group_code VARCHAR(50) NULL;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.aml_shadow_predictions')
      AND name = 'IX_aml_shadow_peer_group_date'
)
    CREATE INDEX IX_aml_shadow_peer_group_date
        ON dbo.aml_shadow_predictions(peer_group_code, evaluated_at DESC)
        INCLUDE (layered_suspicious, layered_final_risk_score, legacy_suspicious, duration_ms);
GO

IF OBJECT_ID('dbo.aml_shadow_scenario_labels', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.aml_shadow_scenario_labels (
        scenario_label_id UNIQUEIDENTIFIER NOT NULL,
        transaction_id VARCHAR(50) NOT NULL,
        scenario_code VARCHAR(100) NOT NULL,
        expected_suspicious BIT NOT NULL,
        labeled_by VARCHAR(100) NOT NULL,
        created_at DATETIME2 NOT NULL
            CONSTRAINT DF_aml_shadow_scenario_created DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_aml_shadow_scenario_labels PRIMARY KEY (scenario_label_id),
        CONSTRAINT UQ_aml_shadow_scenario_transaction UNIQUE (transaction_id, scenario_code)
    );
END;
GO

IF OBJECT_ID('dbo.aml_layered_validations', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.aml_layered_validations (
        validation_id UNIQUEIDENTIFIER NOT NULL,
        risk_policy_version VARCHAR(50) NOT NULL,
        peer_group_code VARCHAR(50) NULL,
        window_started_at DATETIME2 NOT NULL,
        window_ended_at DATETIME2 NOT NULL,
        sample_count BIGINT NOT NULL,
        observation_days INT NOT NULL,
        legacy_alert_count BIGINT NOT NULL,
        layered_alert_count BIGINT NOT NULL,
        overlap_count BIGINT NOT NULL,
        alert_jaccard FLOAT NULL,
        top_risk_overlap_rate FLOAT NULL,
        daily_alert_rate_stddev FLOAT NULL,
        max_segment_daily_stddev FLOAT NULL,
        synthetic_scenario_recall FLOAT NULL,
        reviewed_false_positive_rate FLOAT NULL,
        prediction_latency_p95_ms FLOAT NULL,
        average_incremental_update_ms FLOAT NULL,
        hst_availability_rate FLOAT NOT NULL,
        online_ocsvm_availability_rate FLOAT NOT NULL,
        validation_status VARCHAR(30) NOT NULL,
        blocking_reasons_json NVARCHAR(MAX) NOT NULL,
        warnings_json NVARCHAR(MAX) NOT NULL,
        metrics_json NVARCHAR(MAX) NOT NULL,
        validated_by VARCHAR(100) NOT NULL,
        validated_at DATETIME2 NOT NULL
            CONSTRAINT DF_aml_layered_validation_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_aml_layered_validations PRIMARY KEY (validation_id),
        CONSTRAINT CK_aml_layered_validation_window CHECK (window_started_at <= window_ended_at),
        CONSTRAINT CK_aml_layered_validation_status CHECK (
            validation_status IN ('PASSED', 'FAILED', 'INSUFFICIENT_DATA')
        ),
        CONSTRAINT CK_aml_layered_validation_counts CHECK (
            sample_count >= 0 AND observation_days >= 0 AND legacy_alert_count >= 0
            AND layered_alert_count >= 0 AND overlap_count >= 0
        ),
        CONSTRAINT CK_aml_layered_validation_hst_availability CHECK (hst_availability_rate BETWEEN 0 AND 1),
        CONSTRAINT CK_aml_layered_validation_ocsvm_availability CHECK (
            online_ocsvm_availability_rate BETWEEN 0 AND 1
        ),
        CONSTRAINT CK_aml_layered_validation_blocking_json CHECK (ISJSON(blocking_reasons_json) = 1),
        CONSTRAINT CK_aml_layered_validation_warnings_json CHECK (ISJSON(warnings_json) = 1),
        CONSTRAINT CK_aml_layered_validation_metrics_json CHECK (ISJSON(metrics_json) = 1)
    );
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.aml_layered_validations')
      AND name = 'IX_aml_layered_validations_policy_date'
)
    CREATE INDEX IX_aml_layered_validations_policy_date
        ON dbo.aml_layered_validations(risk_policy_version, peer_group_code, validated_at DESC);
GO

MERGE dbo.app_config AS target
USING (VALUES
    ('aml.layered_validation.min_rows', '1000', 'INTEGER', 'Minimum shadow comparisons required.'),
    ('aml.layered_validation.min_observation_days', '7', 'INTEGER', 'Minimum distinct observation days required.'),
    ('aml.layered_validation.min_legacy_alerts', '20', 'INTEGER', 'Minimum legacy alerts needed for overlap comparison.'),
    ('aml.layered_validation.max_alert_rate', '0.10', 'DECIMAL', 'Maximum acceptable layered suspicious rate.'),
    ('aml.layered_validation.max_alert_volume_increase', '0.50', 'DECIMAL', 'Maximum relative alert-volume increase over legacy.'),
    ('aml.layered_validation.min_top_risk_overlap', '0.50', 'DECIMAL', 'Minimum legacy-alert coverage among the layered top one percent.'),
    ('aml.layered_validation.max_daily_rate_stddev', '0.05', 'DECIMAL', 'Maximum daily layered-alert-rate standard deviation.'),
    ('aml.layered_validation.max_segment_daily_stddev', '0.08', 'DECIMAL', 'Maximum daily alert-rate deviation in any segment.'),
    ('aml.layered_validation.min_synthetic_scenarios', '20', 'INTEGER', 'Minimum expected-positive synthetic scenarios required.'),
    ('aml.layered_validation.min_synthetic_recall', '0.80', 'DECIMAL', 'Minimum recall on labeled synthetic AML scenarios.'),
    ('aml.layered_validation.min_reviewed_alerts', '20', 'INTEGER', 'Minimum reviewed layered alerts required.'),
    ('aml.layered_validation.max_reviewed_false_positive_rate', '0.80', 'DECIMAL', 'Maximum false-positive share among reviewed layered alerts.'),
    ('aml.layered_validation.max_p95_latency_ms', '250', 'DECIMAL', 'Maximum layered shadow p95 latency in milliseconds.'),
    ('aml.layered_validation.min_model_availability', '0.99', 'DECIMAL', 'Minimum score availability for each production ML model.'),
    ('aml.layered_validation.max_average_incremental_update_ms', '3600000', 'DECIMAL', 'Maximum average completed incremental update duration.')
) AS source(config_key, config_value, value_type, description)
ON target.config_key = source.config_key
WHEN NOT MATCHED THEN
    INSERT (config_key, config_value, value_type, description, updated_at)
    VALUES (source.config_key, source.config_value, source.value_type, source.description, SYSUTCDATETIME());
GO

PRINT 'Phase 20 layered shadow validation migration completed.';
GO
