USE [fraud-transaction-detector];
GO

IF COL_LENGTH('dbo.uploaded_datasets', 'source_type') IS NULL
    ALTER TABLE dbo.uploaded_datasets ADD source_type VARCHAR(30) NULL;
GO

UPDATE dbo.uploaded_datasets
SET source_type = 'UPLOAD_BATCH'
WHERE source_type IS NULL;
GO

ALTER TABLE dbo.uploaded_datasets
ALTER COLUMN source_type VARCHAR(30) NOT NULL;
GO

IF COL_LENGTH('dbo.uploaded_datasets', 'snapshot_max_transaction_id') IS NULL
    ALTER TABLE dbo.uploaded_datasets ADD snapshot_max_transaction_id BIGINT NULL;
GO

IF EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.uploaded_datasets')
      AND name = 'source_batch_id'
      AND is_nullable = 0
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE name = 'UX_uploaded_datasets_source_batch_id'
          AND object_id = OBJECT_ID('dbo.uploaded_datasets')
    )
        DROP INDEX UX_uploaded_datasets_source_batch_id ON dbo.uploaded_datasets;

    ALTER TABLE dbo.uploaded_datasets
        ALTER COLUMN source_batch_id BIGINT NULL;
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'UX_uploaded_datasets_source_batch_id'
      AND object_id = OBJECT_ID('dbo.uploaded_datasets')
)
BEGIN
    CREATE UNIQUE INDEX UX_uploaded_datasets_source_batch_id
        ON dbo.uploaded_datasets(source_batch_id)
        WHERE source_batch_id IS NOT NULL;
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'IX_uploaded_datasets_source_type_uploaded_at'
      AND object_id = OBJECT_ID('dbo.uploaded_datasets')
)
BEGIN
    CREATE INDEX IX_uploaded_datasets_source_type_uploaded_at
        ON dbo.uploaded_datasets(source_type, uploaded_at DESC);
END;
GO
