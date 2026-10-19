-- Check CDC (requires SQL Agent, usually Standard+)
BEGIN TRY
    IF NOT EXISTS (SELECT * FROM sys.change_tracking_databases WHERE database_id = DB_ID())
        ALTER DATABASE testdb SET CHANGE_TRACKING = ON;
    SELECT 'Change Tracking: SUPPORTED' AS Feature;
END TRY
BEGIN CATCH
    SELECT 'Change Tracking: ' + ERROR_MESSAGE() AS Feature;
END CATCH;

BEGIN TRY
    EXEC sys.sp_cdc_enable_db;
    SELECT 'CDC: SUPPORTED' AS Feature;
    EXEC sys.sp_cdc_disable_db;
END TRY
BEGIN CATCH
    SELECT 'CDC: ' + ERROR_MESSAGE() AS Feature;
END CATCH;

-- Check TDE (requires Enterprise/Developer historically; 2019+ broadened)
BEGIN TRY
    SELECT TOP 1 'TDE: encryption_state=' + CAST(encryption_state AS NVARCHAR(10)) AS Feature
    FROM sys.dm_database_encryption_keys WHERE database_id = DB_ID();
    IF @@ROWCOUNT = 0 SELECT 'TDE: not enabled on this DB (feasibility unknown)' AS Feature;
END TRY
BEGIN CATCH
    SELECT 'TDE check error: ' + ERROR_MESSAGE() AS Feature;
END CATCH;

-- Temporal tables test
BEGIN TRY
    IF OBJECT_ID('temporal_test_tmp') IS NULL
    BEGIN
        CREATE TABLE temporal_test_tmp (
            id INT PRIMARY KEY,
            data NVARCHAR(50),
            ValidFrom DATETIME2 GENERATED ALWAYS AS ROW START NOT NULL,
            ValidTo DATETIME2 GENERATED ALWAYS AS ROW END NOT NULL,
            PERIOD FOR SYSTEM_TIME (ValidFrom, ValidTo)
        ) WITH (SYSTEM_VERSIONING = ON);
    END
    SELECT 'Temporal tables: SUPPORTED' AS Feature;
    ALTER TABLE temporal_test_tmp SET (SYSTEM_VERSIONING = OFF);
    IF OBJECT_ID('temporal_test_tmp') IS NOT NULL DROP TABLE temporal_test_tmp;
    IF OBJECT_ID('MSSQL_TemporalHistoryFor_temporal_test_tmp') IS NOT NULL
        DROP TABLE MSSQL_TemporalHistoryFor_temporal_test_tmp;
END TRY
BEGIN CATCH
    SELECT 'Temporal tables: ' + ERROR_MESSAGE() AS Feature;
END CATCH;
