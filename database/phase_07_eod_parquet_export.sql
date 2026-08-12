USE [fraud-transaction-detector];
GO

SET XACT_ABORT ON;
GO

IF OBJECT_ID('dbo.aml_training_runs', 'U') IS NULL
    THROW 51071, 'Run phase_01_aml_schema_foundation.sql before Phase 07.', 1;
GO

IF OBJECT_ID('dbo.aml_business_days', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.aml_business_days (
        business_date DATE NOT NULL
            CONSTRAINT PK_aml_business_days PRIMARY KEY,
        status VARCHAR(20) NOT NULL
            CONSTRAINT DF_aml_business_days_status DEFAULT 'OPEN',
        closed_at DATETIME2 NULL,
        closed_by VARCHAR(100) NULL,
        updated_at DATETIME2 NOT NULL
            CONSTRAINT DF_aml_business_days_updated_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT CK_aml_business_days_status CHECK (status IN ('OPEN', 'CLOSED'))
    );
END;
GO

MERGE dbo.app_config AS target
USING (
    VALUES
        ('aml.export.base_path', 'outputs/training-datasets', 'STRING', 'Base directory for versioned Parquet training datasets.'),
        ('aml.export.chunk_size', '50000', 'INTEGER', 'Maximum eligible feature rows read from SQL Server per keyset-paginated chunk.'),
        ('aml.export.rows_per_file', '100000', 'INTEGER', 'Maximum rows written to each Parquet part file.')
) AS source (config_key, config_value, value_type, description)
ON target.config_key = source.config_key
WHEN NOT MATCHED THEN
    INSERT (config_key, config_value, value_type, description, updated_at)
    VALUES (source.config_key, source.config_value, source.value_type, source.description, SYSUTCDATETIME());
GO

PRINT 'Phase 07 end-of-day Parquet export migration completed successfully.';
GO
