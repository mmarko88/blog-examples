SELECT CAST(SERVERPROPERTY('EngineEdition') AS INT) AS EngineEdition,
       CAST(SERVERPROPERTY('ProductMajorVersion') AS INT) AS MajorVersion;
-- Try creating a partition function to test support
BEGIN TRY
    CREATE PARTITION FUNCTION pf_test_support (INT) AS RANGE LEFT FOR VALUES (100000);
    DROP PARTITION FUNCTION pf_test_support;
    SELECT 'Partitioning: SUPPORTED' AS Result;
END TRY
BEGIN CATCH
    SELECT 'Partitioning: NOT SUPPORTED - ' + ERROR_MESSAGE() AS Result;
END CATCH;
