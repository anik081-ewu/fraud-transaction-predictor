USE [fraud-transaction-detector];
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'aml_supervised_growth_studies')
BEGIN
    CREATE TABLE dbo.aml_supervised_growth_studies (
        study_id UNIQUEIDENTIFIER NOT NULL
            CONSTRAINT PK_aml_supervised_growth_studies PRIMARY KEY,
        training_run_id UNIQUEIDENTIFIER NOT NULL,
        status VARCHAR(30) NOT NULL,
        result_json NVARCHAR(MAX) NULL,
        requested_by VARCHAR(100) NULL,
        failure_reason NVARCHAR(4000) NULL,
        started_at DATETIME2(3) NULL,
        completed_at DATETIME2(3) NULL,
        created_at DATETIME2(3) NOT NULL
            CONSTRAINT DF_aml_supervised_growth_studies_created DEFAULT SYSUTCDATETIME()
    );
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'IX_aml_supervised_growth_studies_latest'
      AND object_id = OBJECT_ID('dbo.aml_supervised_growth_studies')
)
BEGIN
    CREATE INDEX IX_aml_supervised_growth_studies_latest
        ON dbo.aml_supervised_growth_studies(status, created_at DESC);
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'IX_aml_supervised_growth_studies_training_run'
      AND object_id = OBJECT_ID('dbo.aml_supervised_growth_studies')
)
BEGIN
    CREATE INDEX IX_aml_supervised_growth_studies_training_run
        ON dbo.aml_supervised_growth_studies(training_run_id, status, created_at DESC);
END;
GO

PRINT 'Supervised growth-study cache is ready.';
GO
