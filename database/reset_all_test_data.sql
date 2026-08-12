USE [fraud-transaction-detector];
GO

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

/*
    TEST/DEVELOPMENT RESET ONLY.

    Preserved tables:
      - dbo.app_users
      - dbo.app_config
      - common migration-history tables

    All other user-table rows are deleted. This includes transactions,
    uploads, cases, alerts, predictions, research runs, training runs,
    model registries, validations, deployment pointers, and profile state.

    Stop Spring Boot and all database-writing workers before running.
*/

DECLARE @Confirmation VARCHAR(30) = 'RESET_TEST_DATA';

IF @Confirmation <> 'RESET_TEST_DATA'
    THROW 51300, 'Reset cancelled. Set @Confirmation to RESET_TEST_DATA.', 1;

DECLARE @PreservedTables TABLE (
    schema_name SYSNAME NOT NULL,
    table_name SYSNAME NOT NULL,
    PRIMARY KEY (schema_name, table_name)
);

INSERT INTO @PreservedTables (schema_name, table_name)
VALUES
    ('dbo', 'app_users'),
    ('dbo', 'app_config'),
    ('dbo', 'flyway_schema_history'),
    ('dbo', 'databasechangelog'),
    ('dbo', 'databasechangeloglock'),
    ('dbo', '__EFMigrationsHistory');

DECLARE @ResetTables TABLE (
    row_no INT IDENTITY(1, 1) PRIMARY KEY,
    object_id INT NOT NULL,
    qualified_name NVARCHAR(517) NOT NULL,
    has_identity BIT NOT NULL,
    before_rows BIGINT NULL,
    after_rows BIGINT NULL
);

INSERT INTO @ResetTables (object_id, qualified_name, has_identity)
SELECT
    tables.object_id,
    QUOTENAME(schemas.name) + N'.' + QUOTENAME(tables.name),
    CASE WHEN EXISTS (
        SELECT 1
        FROM sys.identity_columns identity_columns
        WHERE identity_columns.object_id = tables.object_id
    ) THEN 1 ELSE 0 END
FROM sys.tables tables
JOIN sys.schemas schemas ON schemas.schema_id = tables.schema_id
WHERE tables.is_ms_shipped = 0
  AND NOT EXISTS (
      SELECT 1
      FROM @PreservedTables preserved
      WHERE preserved.schema_name = schemas.name
        AND preserved.table_name = tables.name
  );

IF NOT EXISTS (SELECT 1 FROM @ResetTables)
    THROW 51301, 'No resettable user tables were found.', 1;

DECLARE
    @RowNo INT,
    @QualifiedName NVARCHAR(517),
    @HasIdentity BIT,
    @Sql NVARCHAR(MAX),
    @Count BIGINT;

DECLARE reset_cursor CURSOR LOCAL FAST_FORWARD FOR
SELECT row_no, qualified_name, has_identity
FROM @ResetTables
ORDER BY row_no;

OPEN reset_cursor;
FETCH NEXT FROM reset_cursor INTO @RowNo, @QualifiedName, @HasIdentity;

WHILE @@FETCH_STATUS = 0
BEGIN
    SET @Sql = N'SELECT @CountOutput = COUNT_BIG(*) FROM ' + @QualifiedName + N';';
    EXEC sys.sp_executesql @Sql, N'@CountOutput BIGINT OUTPUT', @CountOutput = @Count OUTPUT;

    UPDATE @ResetTables
    SET before_rows = @Count
    WHERE row_no = @RowNo;

    FETCH NEXT FROM reset_cursor INTO @RowNo, @QualifiedName, @HasIdentity;
END;

CLOSE reset_cursor;
DEALLOCATE reset_cursor;

BEGIN TRY
    BEGIN TRANSACTION;

    DECLARE constraint_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT qualified_name
    FROM @ResetTables
    ORDER BY row_no;

    OPEN constraint_cursor;
    FETCH NEXT FROM constraint_cursor INTO @QualifiedName;

    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @Sql = N'ALTER TABLE ' + @QualifiedName + N' NOCHECK CONSTRAINT ALL;';
        EXEC sys.sp_executesql @Sql;
        FETCH NEXT FROM constraint_cursor INTO @QualifiedName;
    END;

    CLOSE constraint_cursor;
    DEALLOCATE constraint_cursor;

    DECLARE delete_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT qualified_name, has_identity
    FROM @ResetTables
    ORDER BY row_no;

    OPEN delete_cursor;
    FETCH NEXT FROM delete_cursor INTO @QualifiedName, @HasIdentity;

    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @Sql = N'DELETE FROM ' + @QualifiedName + N';';
        EXEC sys.sp_executesql @Sql;

        IF @HasIdentity = 1
        BEGIN
            SET @Sql = N'DBCC CHECKIDENT (' + QUOTENAME(@QualifiedName, '''') + N', RESEED, 0) WITH NO_INFOMSGS;';
            EXEC sys.sp_executesql @Sql;
        END;

        FETCH NEXT FROM delete_cursor INTO @QualifiedName, @HasIdentity;
    END;

    CLOSE delete_cursor;
    DEALLOCATE delete_cursor;

    DECLARE recheck_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT qualified_name
    FROM @ResetTables
    ORDER BY row_no;

    OPEN recheck_cursor;
    FETCH NEXT FROM recheck_cursor INTO @QualifiedName;

    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @Sql = N'ALTER TABLE ' + @QualifiedName + N' WITH CHECK CHECK CONSTRAINT ALL;';
        EXEC sys.sp_executesql @Sql;
        FETCH NEXT FROM recheck_cursor INTO @QualifiedName;
    END;

    CLOSE recheck_cursor;
    DEALLOCATE recheck_cursor;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF CURSOR_STATUS('local', 'constraint_cursor') >= 0
        CLOSE constraint_cursor;
    IF CURSOR_STATUS('local', 'constraint_cursor') > -3
        DEALLOCATE constraint_cursor;

    IF CURSOR_STATUS('local', 'delete_cursor') >= 0
        CLOSE delete_cursor;
    IF CURSOR_STATUS('local', 'delete_cursor') > -3
        DEALLOCATE delete_cursor;

    IF CURSOR_STATUS('local', 'recheck_cursor') >= 0
        CLOSE recheck_cursor;
    IF CURSOR_STATUS('local', 'recheck_cursor') > -3
        DEALLOCATE recheck_cursor;

    IF XACT_STATE() <> 0
        ROLLBACK TRANSACTION;

    THROW;
END CATCH;

DECLARE verify_cursor CURSOR LOCAL FAST_FORWARD FOR
SELECT row_no, qualified_name, has_identity
FROM @ResetTables
ORDER BY row_no;

OPEN verify_cursor;
FETCH NEXT FROM verify_cursor INTO @RowNo, @QualifiedName, @HasIdentity;

WHILE @@FETCH_STATUS = 0
BEGIN
    SET @Sql = N'SELECT @CountOutput = COUNT_BIG(*) FROM ' + @QualifiedName + N';';
    EXEC sys.sp_executesql @Sql, N'@CountOutput BIGINT OUTPUT', @CountOutput = @Count OUTPUT;

    UPDATE @ResetTables
    SET after_rows = @Count
    WHERE row_no = @RowNo;

    FETCH NEXT FROM verify_cursor INTO @RowNo, @QualifiedName, @HasIdentity;
END;

CLOSE verify_cursor;
DEALLOCATE verify_cursor;

SELECT
    qualified_name AS table_name,
    before_rows,
    after_rows,
    has_identity
FROM @ResetTables
ORDER BY qualified_name;

SELECT
    (SELECT COUNT_BIG(*) FROM dbo.app_users) AS preserved_users,
    (SELECT COUNT_BIG(*) FROM dbo.app_config) AS preserved_app_config_rows;

PRINT 'Test data reset completed. Users and app_config were preserved.';
GO
