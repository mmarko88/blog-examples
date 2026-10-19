-- Check In-Memory OLTP support
BEGIN TRY
    IF NOT EXISTS (SELECT * FROM sys.filegroups WHERE type = 'FX')
    BEGIN
        DECLARE @dbPath NVARCHAR(500);
        SELECT @dbPath = physical_name FROM sys.master_files
            WHERE database_id = DB_ID() AND type = 0;
        SET @dbPath = REPLACE(@dbPath, '.mdf', '_inmem');
        -- Add filegroup
        DECLARE @sql NVARCHAR(MAX) = N'ALTER DATABASE testdb ADD FILEGROUP imoltp_fg CONTAINS MEMORY_OPTIMIZED_DATA;';
        EXEC(@sql);
        SET @sql = N'ALTER DATABASE testdb ADD FILE (name=''imoltp'', filename=''' + @dbPath + ''') TO FILEGROUP imoltp_fg;';
        EXEC(@sql);
    END
    SELECT 'In-Memory OLTP: SUPPORTED' AS Result;
END TRY
BEGIN CATCH
    SELECT 'In-Memory OLTP: ' + ERROR_MESSAGE() AS Result;
END CATCH;
