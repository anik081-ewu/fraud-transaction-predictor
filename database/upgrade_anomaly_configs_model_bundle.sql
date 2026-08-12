USE [fraud-transaction-detector];
GO

IF OBJECT_ID('dbo.anomaly_configs', 'U') IS NULL
    THROW 50001, 'Table dbo.anomaly_configs does not exist.', 1;
GO

IF COL_LENGTH('dbo.anomaly_configs', 'dataset_partition_id') IS NULL
    ALTER TABLE dbo.anomaly_configs
        ADD dataset_partition_id BIGINT NULL;
GO

IF COL_LENGTH('dbo.anomaly_configs', 'artifact_base_path') IS NULL
    ALTER TABLE dbo.anomaly_configs
        ADD artifact_base_path VARCHAR(500) NULL;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'IX_anomaly_configs_dataset_partition_id'
      AND object_id = OBJECT_ID('dbo.anomaly_configs')
)
BEGIN
    CREATE INDEX IX_anomaly_configs_dataset_partition_id
        ON dbo.anomaly_configs(dataset_partition_id);
END;
GO
