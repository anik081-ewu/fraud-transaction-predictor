-- Add QUEUED to the allowed status values for bulk_upload_batches.
-- Required for async upload: the batch is created as QUEUED before the background
-- processor picks it up and transitions it to PROCESSING -> COMPLETED/FAILED.

ALTER TABLE dbo.bulk_upload_batches
    DROP CONSTRAINT chk_bulk_upload_batches_status;

ALTER TABLE dbo.bulk_upload_batches
    ADD CONSTRAINT chk_bulk_upload_batches_status
    CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED'));
