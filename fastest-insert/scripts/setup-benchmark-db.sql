-- Manual DB setup (alternative to setup-linux.sh / setup-mssql-native.sh).
-- Idempotent — safe to re-run.
--   sqlcmd -S localhost -U sa -C -i setup-benchmark-db.sql

IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name='weblogic')
    CREATE LOGIN weblogic WITH PASSWORD='weblogic', CHECK_POLICY=OFF;
GO
IF NOT EXISTS (SELECT 1 FROM sys.databases WHERE name='testdb')
    CREATE DATABASE testdb;
GO
USE testdb;
GO
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name='weblogic')
    CREATE USER weblogic FOR LOGIN weblogic;
IF IS_ROLEMEMBER('db_owner','weblogic') <> 1
    ALTER ROLE db_owner ADD MEMBER weblogic;
GO
-- Part 8 (TVP)
IF NOT EXISTS (SELECT 1 FROM sys.types WHERE name='PersonTableType')
    CREATE TYPE dbo.PersonTableType AS TABLE (
        person_id INT, user_name NVARCHAR(30),
        first_name NVARCHAR(10), last_name NVARCHAR(15), years INT);
GO
