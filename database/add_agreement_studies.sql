USE [fraud-transaction-detector];
GO

/*
    Persists model-agreement analyses so the comparison UI can show them instantly.

    Scoring a full snapshot with every model takes minutes, so it cannot run on page load.
    The result is a small matrix that is always read as a whole, so it is stored as one JSON
    payload rather than shredded into columns — unlike growth metrics, nothing queries
    individual cells.
*/

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'aml_agreement_studies')
BEGIN
    CREATE TABLE dbo.aml_agreement_studies (
        study_id UNIQUEIDENTIFIER NOT NULL
            CONSTRAINT PK_aml_agreement_studies PRIMARY KEY,
        training_run_id UNIQUEIDENTIFIER NOT NULL,
        status VARCHAR(30) NOT NULL,
        evaluated_rows BIGINT NULL,
        model_count INT NULL,
        result_json NVARCHAR(MAX) NULL,
        requested_by VARCHAR(100) NULL,
        failure_reason NVARCHAR(4000) NULL,
        started_at DATETIME2(3) NULL,
        completed_at DATETIME2(3) NULL,
        created_at DATETIME2(3) NOT NULL
            CONSTRAINT DF_aml_agreement_studies_created DEFAULT SYSUTCDATETIME()
    );
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_aml_agreement_studies_status_created')
    CREATE INDEX IX_aml_agreement_studies_status_created
        ON dbo.aml_agreement_studies(status, created_at DESC);
GO

SELECT COUNT_BIG(*) AS agreement_studies FROM dbo.aml_agreement_studies;

PRINT 'Model agreement study table is ready.';
GO
