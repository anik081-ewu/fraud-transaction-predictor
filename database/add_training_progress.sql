USE [fraud-transaction-detector];
GO

/*
    Adds live progress tracking to training runs so the UI can render a real
    percentage bar instead of only a coarse status chip.

    progress_stage   sub-phase inside the current status (MATERIALIZING /
                     EXPORTING / TRAINING / COMPLETED) — the status column alone
                     cannot express this because feature materialization runs
                     while the run is still QUEUED.
    progress_current rows finished so far in the current stage
    progress_total   rows the current stage has to finish

    Existing columns are left alone: requested_row_count and exported_row_count
    are load-bearing for the export consistency check and must not be reused.
*/

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.aml_training_runs') AND name = 'progress_stage'
)
BEGIN
    ALTER TABLE dbo.aml_training_runs ADD progress_stage NVARCHAR(40) NULL;
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.aml_training_runs') AND name = 'progress_current'
)
BEGIN
    ALTER TABLE dbo.aml_training_runs ADD progress_current BIGINT NULL;
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.aml_training_runs') AND name = 'progress_total'
)
BEGIN
    ALTER TABLE dbo.aml_training_runs ADD progress_total BIGINT NULL;
END;
GO

SELECT name, system_type_id, is_nullable
FROM sys.columns
WHERE object_id = OBJECT_ID('dbo.aml_training_runs')
  AND name IN ('progress_stage', 'progress_current', 'progress_total')
ORDER BY name;

PRINT 'Training run progress columns are ready.';
GO
