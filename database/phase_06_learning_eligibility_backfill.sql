USE [fraud-transaction-detector];
GO

SET XACT_ABORT ON;
GO

IF OBJECT_ID('dbo.aml_feature_learning_status', 'U') IS NULL
    THROW 51061, 'Run phase_01_aml_schema_foundation.sql before Phase 06.', 1;
GO

INSERT INTO dbo.aml_feature_learning_status (
    transaction_id, eligibility_status, eligibility_reason,
    eligible_for_incremental_model, eligible_for_trusted_profile,
    eligible_for_batch_training, reviewed_by, reviewed_at, updated_at
)
SELECT
    feature.transaction_id,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM dbo.fraud_alerts alert
            WHERE alert.transaction_id = feature.transaction_id
              AND alert.review_status = 'STR_GENERATED'
        ) THEN 'DO_NOT_LEARN'
        WHEN EXISTS (
            SELECT 1 FROM dbo.fraud_alerts alert
            WHERE alert.transaction_id = feature.transaction_id
              AND COALESCE(alert.review_status, 'PENDING') <> 'FALSE_POSITIVE'
        ) THEN 'WAIT_FOR_REVIEW'
        ELSE 'LEARN_IMMEDIATELY'
    END,
    CASE
        WHEN EXISTS (
            SELECT 1 FROM dbo.fraud_alerts alert
            WHERE alert.transaction_id = feature.transaction_id
              AND alert.review_status = 'STR_GENERATED'
        ) THEN 'Historical STR generated; excluded from learning'
        WHEN EXISTS (
            SELECT 1 FROM dbo.fraud_alerts alert
            WHERE alert.transaction_id = feature.transaction_id
              AND COALESCE(alert.review_status, 'PENDING') <> 'FALSE_POSITIVE'
        ) THEN 'Historical alert requires review before learning'
        ELSE 'Historical transaction accepted for learning'
    END,
    CASE WHEN EXISTS (
        SELECT 1 FROM dbo.fraud_alerts alert
        WHERE alert.transaction_id = feature.transaction_id
          AND COALESCE(alert.review_status, 'PENDING') <> 'FALSE_POSITIVE'
    ) THEN 0 ELSE 1 END,
    CASE WHEN EXISTS (
        SELECT 1 FROM dbo.fraud_alerts alert
        WHERE alert.transaction_id = feature.transaction_id
          AND COALESCE(alert.review_status, 'PENDING') <> 'FALSE_POSITIVE'
    ) THEN 0 ELSE 1 END,
    CASE WHEN EXISTS (
        SELECT 1 FROM dbo.fraud_alerts alert
        WHERE alert.transaction_id = feature.transaction_id
          AND COALESCE(alert.review_status, 'PENDING') <> 'FALSE_POSITIVE'
    ) THEN 0 ELSE 1 END,
    reviewed.reviewed_by,
    reviewed.reviewed_at,
    SYSUTCDATETIME()
FROM dbo.aml_transaction_features feature
OUTER APPLY (
    SELECT TOP (1) alert.reviewed_by, alert.reviewed_at
    FROM dbo.fraud_alerts alert
    WHERE alert.transaction_id = feature.transaction_id
      AND alert.review_status IN ('FALSE_POSITIVE', 'STR_GENERATED')
    ORDER BY alert.reviewed_at DESC, alert.id DESC
) reviewed
WHERE NOT EXISTS (
    SELECT 1
    FROM dbo.aml_feature_learning_status learning
    WHERE learning.transaction_id = feature.transaction_id
);
GO

PRINT 'Phase 06 learning eligibility backfill completed successfully.';
GO
