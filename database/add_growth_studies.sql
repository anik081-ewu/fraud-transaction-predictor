USE [fraud-transaction-detector];
GO

/*
    Persists data-growth studies so the comparison UI can display them instantly.

    A study trains every detector at every partition (5 x 4 = 20 fits) and takes many
    minutes. Storing the result turns it into a one-off cost: the page reads rows rather
    than re-running the analysis, the same pattern the model-comparison table already uses.

    aml_growth_studies  one row per run of the analysis
    aml_growth_metrics  one row per detector x partition cell
*/

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'aml_growth_studies')
BEGIN
    CREATE TABLE dbo.aml_growth_studies (
        study_id UNIQUEIDENTIFIER NOT NULL
            CONSTRAINT PK_aml_growth_studies PRIMARY KEY,
        training_run_id UNIQUEIDENTIFIER NOT NULL,
        status VARCHAR(30) NOT NULL,
        feature_version VARCHAR(30) NULL,
        dataset_rows BIGINT NULL,
        feature_count INT NULL,
        partition_percentages VARCHAR(100) NULL,
        methodology_json NVARCHAR(MAX) NULL,
        requested_by VARCHAR(100) NULL,
        failure_reason NVARCHAR(4000) NULL,
        started_at DATETIME2(3) NULL,
        completed_at DATETIME2(3) NULL,
        created_at DATETIME2(3) NOT NULL
            CONSTRAINT DF_aml_growth_studies_created DEFAULT SYSUTCDATETIME()
    );
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'aml_growth_metrics')
BEGIN
    CREATE TABLE dbo.aml_growth_metrics (
        id BIGINT IDENTITY(1, 1) NOT NULL
            CONSTRAINT PK_aml_growth_metrics PRIMARY KEY,
        study_id UNIQUEIDENTIFIER NOT NULL,
        detector VARCHAR(50) NOT NULL,
        partition_percentage INT NOT NULL,
        partition_rows BIGINT NULL,
        training_rows BIGINT NULL,
        learned_rows BIGINT NULL,
        evaluation_rows BIGINT NULL,
        excess_mass_auc FLOAT NULL,
        score_skewness FLOAT NULL,
        rank_stability FLOAT NULL,
        anomaly_rate FLOAT NULL,
        alert_count BIGINT NULL,
        threshold FLOAT NULL,
        average_score FLOAT NULL,
        score_p50 FLOAT NULL,
        score_p95 FLOAT NULL,
        score_p99 FLOAT NULL,
        training_duration_ms FLOAT NULL,
        rows_per_second FLOAT NULL,
        bounded_training_sample BIT NOT NULL
            CONSTRAINT DF_aml_growth_metrics_bounded DEFAULT 0,
        created_at DATETIME2(3) NOT NULL
            CONSTRAINT DF_aml_growth_metrics_created DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_aml_growth_metrics_study FOREIGN KEY (study_id)
            REFERENCES dbo.aml_growth_studies(study_id) ON DELETE CASCADE
    );
END;
GO

-- The page always loads the newest completed study first
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_aml_growth_studies_status_created')
    CREATE INDEX IX_aml_growth_studies_status_created
        ON dbo.aml_growth_studies(status, created_at DESC);
GO

-- Metrics are always fetched for one study, ordered as the matrix is rendered
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_aml_growth_metrics_study')
    CREATE INDEX IX_aml_growth_metrics_study
        ON dbo.aml_growth_metrics(study_id, detector, partition_percentage);
GO

SELECT
    (SELECT COUNT_BIG(*) FROM dbo.aml_growth_studies) AS studies,
    (SELECT COUNT_BIG(*) FROM dbo.aml_growth_metrics) AS metrics;

PRINT 'Growth study tables are ready.';
GO
