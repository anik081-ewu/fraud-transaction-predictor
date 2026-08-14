USE [fraud-transaction-detector];
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'IX_case_records_status_created_at'
      AND object_id = OBJECT_ID('dbo.case_records')
)
BEGIN
    CREATE INDEX IX_case_records_status_created_at
        ON dbo.case_records(status, created_at DESC, id DESC)
        INCLUDE (case_no, fraud_alert_id, transaction_id, account_id, title, priority, assigned_to, created_by, updated_at);
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'IX_case_notes_case_record_created_at'
      AND object_id = OBJECT_ID('dbo.case_notes')
)
BEGIN
    CREATE INDEX IX_case_notes_case_record_created_at
        ON dbo.case_notes(case_record_id, created_at ASC, id ASC)
        INCLUDE (note_text, created_by);
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'IX_fraud_prediction_logs_transaction_created_at'
      AND object_id = OBJECT_ID('dbo.fraud_prediction_logs')
)
BEGIN
    CREATE INDEX IX_fraud_prediction_logs_transaction_created_at
        ON dbo.fraud_prediction_logs(transaction_id, created_at DESC, id DESC);
END;
GO
