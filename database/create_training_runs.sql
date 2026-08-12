USE [fraud-transaction-detector];
GO

IF OBJECT_ID('dbo.training_runs', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.training_runs (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        training_run_no VARCHAR(50) NOT NULL UNIQUE,
        source VARCHAR(50) NOT NULL,
        requested_by VARCHAR(100) NULL,
        status VARCHAR(30) NOT NULL,
        training_row_count INT NULL,
        feature_count INT NULL,
        models_json NVARCHAR(MAX) NULL,
        artifacts_json NVARCHAR(MAX) NULL,
        hyperparams_json NVARCHAR(MAX) NULL,
        response_status VARCHAR(30) NULL,
        message NVARCHAR(MAX) NULL,
        started_at DATETIME2 NOT NULL,
        completed_at DATETIME2 NULL,
        duration_ms BIGINT NULL
    );

    CREATE INDEX IX_training_runs_started_at
        ON dbo.training_runs (started_at DESC);

    CREATE INDEX IX_training_runs_status
        ON dbo.training_runs (status);
END;
GO

IF COL_LENGTH('dbo.training_runs', 'requested_by') IS NULL
    ALTER TABLE dbo.training_runs ADD requested_by VARCHAR(100) NULL;
GO

IF COL_LENGTH('dbo.training_runs', 'training_row_count') IS NULL
    ALTER TABLE dbo.training_runs ADD training_row_count INT NULL;
GO

IF COL_LENGTH('dbo.training_runs', 'feature_count') IS NULL
    ALTER TABLE dbo.training_runs ADD feature_count INT NULL;
GO

IF COL_LENGTH('dbo.training_runs', 'models_json') IS NULL
    ALTER TABLE dbo.training_runs ADD models_json NVARCHAR(MAX) NULL;
GO

IF COL_LENGTH('dbo.training_runs', 'artifacts_json') IS NULL
    ALTER TABLE dbo.training_runs ADD artifacts_json NVARCHAR(MAX) NULL;
GO

IF COL_LENGTH('dbo.training_runs', 'hyperparams_json') IS NULL
    ALTER TABLE dbo.training_runs ADD hyperparams_json NVARCHAR(MAX) NULL;
GO

IF COL_LENGTH('dbo.training_runs', 'response_status') IS NULL
    ALTER TABLE dbo.training_runs ADD response_status VARCHAR(30) NULL;
GO

IF COL_LENGTH('dbo.training_runs', 'message') IS NULL
    ALTER TABLE dbo.training_runs ADD message NVARCHAR(MAX) NULL;
GO

IF COL_LENGTH('dbo.training_runs', 'completed_at') IS NULL
    ALTER TABLE dbo.training_runs ADD completed_at DATETIME2 NULL;
GO

IF COL_LENGTH('dbo.training_runs', 'duration_ms') IS NULL
    ALTER TABLE dbo.training_runs ADD duration_ms BIGINT NULL;
GO
