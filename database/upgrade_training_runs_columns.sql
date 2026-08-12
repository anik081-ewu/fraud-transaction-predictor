USE [fraud-transaction-detector];
GO

IF OBJECT_ID('dbo.training_runs', 'U') IS NULL
BEGIN
    THROW 50001, 'dbo.training_runs does not exist. Run create_training_runs.sql first.', 1;
END;
GO

IF COL_LENGTH('dbo.training_runs', 'training_run_no') IS NULL
BEGIN
    IF COL_LENGTH('dbo.training_runs', 'run_no') IS NOT NULL
        EXEC sp_rename 'dbo.training_runs.run_no', 'training_run_no', 'COLUMN';
    ELSE
        ALTER TABLE dbo.training_runs ADD training_run_no VARCHAR(50) NULL;
END;
GO

IF COL_LENGTH('dbo.training_runs', 'source') IS NULL
    ALTER TABLE dbo.training_runs ADD source VARCHAR(50) NULL;
GO

IF COL_LENGTH('dbo.training_runs', 'requested_by') IS NULL
    ALTER TABLE dbo.training_runs ADD requested_by VARCHAR(100) NULL;
GO

IF COL_LENGTH('dbo.training_runs', 'status') IS NULL
    ALTER TABLE dbo.training_runs ADD status VARCHAR(30) NULL;
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

IF COL_LENGTH('dbo.training_runs', 'started_at') IS NULL
    ALTER TABLE dbo.training_runs ADD started_at DATETIME2 NULL;
GO

IF COL_LENGTH('dbo.training_runs', 'completed_at') IS NULL
    ALTER TABLE dbo.training_runs ADD completed_at DATETIME2 NULL;
GO

IF COL_LENGTH('dbo.training_runs', 'duration_ms') IS NULL
    ALTER TABLE dbo.training_runs ADD duration_ms BIGINT NULL;
GO

IF COL_LENGTH('dbo.training_runs', 'dataset_partition_id') IS NOT NULL
    ALTER TABLE dbo.training_runs ALTER COLUMN dataset_partition_id BIGINT NULL;
GO

IF COL_LENGTH('dbo.training_runs', 'model_name') IS NOT NULL
    ALTER TABLE dbo.training_runs ALTER COLUMN model_name VARCHAR(100) NULL;
GO

IF COL_LENGTH('dbo.training_runs', 'training_status') IS NOT NULL
    ALTER TABLE dbo.training_runs ALTER COLUMN training_status VARCHAR(30) NULL;
GO

IF COL_LENGTH('dbo.training_runs', 'rows_used') IS NOT NULL
    ALTER TABLE dbo.training_runs ALTER COLUMN rows_used INT NULL;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'UX_training_runs_training_run_no'
      AND object_id = OBJECT_ID('dbo.training_runs')
)
BEGIN
    CREATE UNIQUE INDEX UX_training_runs_training_run_no
        ON dbo.training_runs(training_run_no)
        WHERE training_run_no IS NOT NULL;
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'IX_training_runs_started_at'
      AND object_id = OBJECT_ID('dbo.training_runs')
)
BEGIN
    CREATE INDEX IX_training_runs_started_at
        ON dbo.training_runs(started_at DESC);
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'IX_training_runs_status'
      AND object_id = OBJECT_ID('dbo.training_runs')
)
BEGIN
    CREATE INDEX IX_training_runs_status
        ON dbo.training_runs(status);
END;
GO
