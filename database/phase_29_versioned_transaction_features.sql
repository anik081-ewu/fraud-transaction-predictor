USE [fraud-transaction-detector];
GO

/*
Allow immutable feature rows from multiple feature-engineering versions to coexist.
Existing AML_FEATURES_V2 rows remain untouched; AML_FEATURES_V3 is inserted separately.
*/
IF EXISTS (
    SELECT 1
    FROM sys.key_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.aml_transaction_features')
      AND name = 'UQ_aml_features_transaction'
)
BEGIN
    ALTER TABLE dbo.aml_transaction_features
        DROP CONSTRAINT UQ_aml_features_transaction;
END;
GO

IF EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.aml_transaction_features')
      AND name = 'UQ_aml_features_transaction'
)
BEGIN
    DROP INDEX UQ_aml_features_transaction ON dbo.aml_transaction_features;
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.aml_transaction_features')
      AND name = 'UX_aml_features_transaction_version'
)
BEGIN
    CREATE UNIQUE INDEX UX_aml_features_transaction_version
        ON dbo.aml_transaction_features(transaction_id, feature_version);
END;
GO

SELECT
    index_name = index_row.name,
    index_row.is_unique,
    column_name = column_row.name,
    index_column.key_ordinal
FROM sys.indexes index_row
INNER JOIN sys.index_columns index_column
    ON index_column.object_id = index_row.object_id
   AND index_column.index_id = index_row.index_id
INNER JOIN sys.columns column_row
    ON column_row.object_id = index_column.object_id
   AND column_row.column_id = index_column.column_id
WHERE index_row.object_id = OBJECT_ID('dbo.aml_transaction_features')
  AND index_row.name = 'UX_aml_features_transaction_version'
ORDER BY index_column.key_ordinal;
GO
