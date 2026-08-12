USE [fraud-transaction-detector];
GO

IF COL_LENGTH('dbo.uploaded_datasets', 'source_batch_id') IS NULL
BEGIN
    ALTER TABLE dbo.uploaded_datasets
        ADD source_batch_id BIGINT NULL;
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.foreign_keys
    WHERE name = 'FK_uploaded_datasets_bulk_upload_batches'
)
BEGIN
    ALTER TABLE dbo.uploaded_datasets
        ADD CONSTRAINT FK_uploaded_datasets_bulk_upload_batches
        FOREIGN KEY (source_batch_id)
        REFERENCES dbo.bulk_upload_batches(id);
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
