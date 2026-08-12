USE [fraud-transaction-detector];
GO

IF COL_LENGTH('dbo.model_versions', 'lifecycle_status') IS NULL
    ALTER TABLE dbo.model_versions ADD lifecycle_status VARCHAR(30) NULL;
GO

UPDATE dbo.model_versions
SET lifecycle_status = CASE WHEN is_active = 1 THEN 'PROMOTED' ELSE 'CANDIDATE' END
WHERE lifecycle_status IS NULL;
GO

ALTER TABLE dbo.model_versions
ALTER COLUMN lifecycle_status VARCHAR(30) NOT NULL;
GO

IF COL_LENGTH('dbo.model_versions', 'promoted_at') IS NULL
    ALTER TABLE dbo.model_versions ADD promoted_at DATETIME2 NULL;
GO

IF COL_LENGTH('dbo.model_versions', 'promoted_by') IS NULL
    ALTER TABLE dbo.model_versions ADD promoted_by VARCHAR(100) NULL;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'UX_model_versions_one_active_per_model'
      AND object_id = OBJECT_ID('dbo.model_versions')
)
BEGIN
    CREATE UNIQUE INDEX UX_model_versions_one_active_per_model
        ON dbo.model_versions(model_name)
        WHERE is_active = 1;
END;
GO
