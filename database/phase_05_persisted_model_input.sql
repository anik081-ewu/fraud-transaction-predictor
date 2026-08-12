USE [fraud-transaction-detector];
GO

SET XACT_ABORT ON;
GO

IF OBJECT_ID('dbo.aml_transaction_features', 'U') IS NULL
    THROW 51051, 'Run phase_01_aml_schema_foundation.sql before Phase 05.', 1;
GO

IF COL_LENGTH('dbo.aml_transaction_features', 'model_feature_schema') IS NULL
    ALTER TABLE dbo.aml_transaction_features
        ADD model_feature_schema VARCHAR(50) NULL;
GO

IF COL_LENGTH('dbo.aml_transaction_features', 'model_features_json') IS NULL
    ALTER TABLE dbo.aml_transaction_features
        ADD model_features_json NVARCHAR(MAX) NULL;
GO

UPDATE dbo.aml_transaction_features
SET model_feature_schema = COALESCE(model_feature_schema, 'LEGACY_UNAVAILABLE'),
    model_features_json = COALESCE(model_features_json, N'{}')
WHERE model_feature_schema IS NULL
   OR model_features_json IS NULL;
GO

ALTER TABLE dbo.aml_transaction_features
    ALTER COLUMN model_feature_schema VARCHAR(50) NOT NULL;
GO

ALTER TABLE dbo.aml_transaction_features
    ALTER COLUMN model_features_json NVARCHAR(MAX) NOT NULL;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = 'CK_aml_features_model_features_json'
      AND parent_object_id = OBJECT_ID('dbo.aml_transaction_features')
)
    ALTER TABLE dbo.aml_transaction_features
        ADD CONSTRAINT CK_aml_features_model_features_json
        CHECK (ISJSON(model_features_json) = 1);
GO

PRINT 'Phase 05 persisted model input migration completed successfully.';
GO
