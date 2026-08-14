USE [fraud-transaction-detector];
GO

IF COL_LENGTH('dbo.transactions', 'fraud_label') IS NULL
    ALTER TABLE dbo.transactions ADD fraud_label BIT NULL;
GO

IF COL_LENGTH('dbo.transactions', 'label_source') IS NULL
    ALTER TABLE dbo.transactions ADD label_source VARCHAR(50) NULL;
GO

IF COL_LENGTH('dbo.transactions', 'labeled_by') IS NULL
    ALTER TABLE dbo.transactions ADD labeled_by VARCHAR(100) NULL;
GO

IF COL_LENGTH('dbo.transactions', 'labeled_at') IS NULL
    ALTER TABLE dbo.transactions ADD labeled_at DATETIME2 NULL;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = 'CK_transactions_fraud_label'
      AND parent_object_id = OBJECT_ID('dbo.transactions')
)
BEGIN
    ALTER TABLE dbo.transactions
        ADD CONSTRAINT CK_transactions_fraud_label
        CHECK (fraud_label IS NULL OR fraud_label IN (0, 1));
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'IX_transactions_fraud_label_transaction_date'
      AND object_id = OBJECT_ID('dbo.transactions')
)
BEGIN
    CREATE INDEX IX_transactions_fraud_label_transaction_date
        ON dbo.transactions(fraud_label, transaction_date)
        INCLUDE (transaction_id, business_date)
        WHERE fraud_label IS NOT NULL;
END;
GO

SELECT
    COUNT_BIG(*) AS labeled_rows,
    SUM(CASE WHEN fraud_label = 1 THEN 1 ELSE 0 END) AS positive_rows,
    SUM(CASE WHEN fraud_label = 0 THEN 1 ELSE 0 END) AS negative_rows
FROM dbo.transactions
WHERE fraud_label IS NOT NULL;
GO
