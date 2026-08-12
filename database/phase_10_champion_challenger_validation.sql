USE [fraud-transaction-detector];
GO

IF OBJECT_ID('dbo.aml_model_registry', 'U') IS NULL
    THROW 51100, 'Run phases 01 through 09 before phase 10.', 1;
GO

IF OBJECT_ID('dbo.aml_model_validations', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.aml_model_validations (
        validation_id UNIQUEIDENTIFIER NOT NULL
            CONSTRAINT PK_aml_model_validations PRIMARY KEY,
        model_version VARCHAR(100) NOT NULL,
        comparison_target VARCHAR(100) NOT NULL,
        window_started_at DATETIME2 NOT NULL,
        window_ended_at DATETIME2 NOT NULL,
        sample_count BIGINT NOT NULL,
        candidate_anomaly_count BIGINT NOT NULL,
        production_alert_count BIGINT NOT NULL,
        overlap_count BIGINT NOT NULL,
        candidate_only_count BIGINT NOT NULL,
        production_only_count BIGINT NOT NULL,
        candidate_anomaly_rate FLOAT NOT NULL,
        production_alert_rate FLOAT NOT NULL,
        agreement_rate FLOAT NOT NULL,
        alert_jaccard FLOAT NULL,
        average_score FLOAT NULL,
        score_stddev FLOAT NULL,
        score_p50 FLOAT NULL,
        score_p95 FLOAT NULL,
        score_p99 FLOAT NULL,
        daily_anomaly_rate_stddev FLOAT NULL,
        reviewed_overlap_count BIGINT NOT NULL,
        false_positive_overlap_count BIGINT NOT NULL,
        str_overlap_count BIGINT NOT NULL,
        reviewed_precision FLOAT NULL,
        validation_status VARCHAR(30) NOT NULL,
        failure_reason VARCHAR(1000) NULL,
        metrics_json NVARCHAR(MAX) NOT NULL,
        validated_by VARCHAR(100) NOT NULL,
        validated_at DATETIME2 NOT NULL
            CONSTRAINT DF_aml_validation_validated_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_aml_validation_model FOREIGN KEY (model_version)
            REFERENCES dbo.aml_model_registry(model_version),
        CONSTRAINT CK_aml_validation_status CHECK (validation_status IN (
            'PASSED', 'FAILED', 'INSUFFICIENT_DATA'
        )),
        CONSTRAINT CK_aml_validation_window CHECK (window_started_at <= window_ended_at)
    );
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.aml_model_validations')
      AND name = 'IX_aml_validations_model_date'
)
    CREATE INDEX IX_aml_validations_model_date
        ON dbo.aml_model_validations(model_version, validated_at DESC);
GO

MERGE dbo.app_config AS target
USING (VALUES
    ('aml.validation.min_rows', '1000', 'INTEGER', 'Minimum silent predictions required to validate a candidate.'),
    ('aml.validation.min_anomaly_rate', '0.001', 'DECIMAL', 'Minimum acceptable challenger anomaly rate.'),
    ('aml.validation.max_anomaly_rate', '0.10', 'DECIMAL', 'Maximum acceptable challenger anomaly rate.'),
    ('aml.validation.max_daily_rate_stddev', '0.05', 'DECIMAL', 'Maximum acceptable daily anomaly-rate standard deviation.'),
    ('aml.validation.min_reviewed_alerts', '20', 'INTEGER', 'Minimum reviewed overlap alerts before analyst precision becomes a gate.'),
    ('aml.validation.min_reviewed_precision', '0.20', 'DECIMAL', 'Minimum STR share among reviewed overlapping alerts.')
) AS source(config_key, config_value, value_type, description)
ON target.config_key = source.config_key
WHEN NOT MATCHED THEN
    INSERT (config_key, config_value, value_type, description, updated_at)
    VALUES (source.config_key, source.config_value, source.value_type, source.description, SYSUTCDATETIME());
GO

PRINT 'Phase 10 champion-challenger validation migration completed.';
GO
