<#
.SYNOPSIS
    Sets up MS SQL Server for the fastest-insert benchmark suite on Windows.

.DESCRIPTION
    Configures a local SQL Server Express instance (or full edition) for running
    the blog-examples/fastest-insert benchmarks. Creates the testdb database,
    weblogic login, and the PersonTableType TVP needed by Part 8.

    Prerequisites:
    - SQL Server (Express or full) installed and running
    - SqlServer PowerShell module (Install-Module SqlServer)
    - TCP/IP enabled on port 1433 (SQL Server Configuration Manager)

.PARAMETER ServerInstance
    SQL Server instance. Default: localhost,1433

.PARAMETER SaUser
    SA user for initial setup. Default: uses Windows auth (current user)

.EXAMPLE
    .\setup-windows.ps1
    .\setup-windows.ps1 -ServerInstance "localhost\SQLEXPRESS"
#>
param(
    [string]$ServerInstance = "localhost,1433",
    [string]$SaUser = ""
)

$ErrorActionPreference = "Stop"

function Invoke-Sql {
    param([string]$Query, [string]$Database = "master")
    $params = @{
        ServerInstance       = $ServerInstance
        Database             = $Database
        Query                = $Query
        TrustServerCertificate = $true
    }
    if ($SaUser) {
        $params.Username = $SaUser
        $cred = Read-Host "Password for $SaUser" -AsSecureString
        $params.Password = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
            [Runtime.InteropServices.Marshal]::SecureStringToBSTR($cred))
    }
    Invoke-Sqlcmd @params
}

Write-Host "=== Fastest-Insert DB Setup (Windows) ===" -ForegroundColor Cyan
Write-Host "Server: $ServerInstance"

# 1. Enable mixed-mode auth (SQL + Windows)
Write-Host "`n[1/5] Checking authentication mode..."
$authMode = Invoke-Sql -Query "SELECT SERVERPROPERTY('IsIntegratedSecurityOnly') AS IsWindowsAuthOnly"
if ($authMode.IsWindowsAuthOnly -eq 1) {
    Write-Host "  Mixed mode not enabled. Enabling via registry..." -ForegroundColor Yellow
    $instanceName = if ($ServerInstance -match '\\(.+)') { $Matches[1] } else { "MSSQLSERVER" }
    $regPath = "HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server\MSSQL*.$instanceName\MSSQLServer"
    $actualPath = (Get-ChildItem "HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server" |
        Where-Object { $_.PSChildName -match "MSSQL\d+\.$instanceName" } |
        Select-Object -First 1).PSPath + "\MSSQLServer"
    Set-ItemProperty -Path $actualPath -Name "LoginMode" -Value 2
    Write-Host "  Mixed mode enabled. Restart SQL Server to take effect." -ForegroundColor Yellow
    Write-Host "  Run: Restart-Service MSSQL`$$instanceName" -ForegroundColor Yellow
} else {
    Write-Host "  Mixed mode already enabled." -ForegroundColor Green
}

# 2. Create testdb
Write-Host "`n[2/5] Creating testdb database..."
Invoke-Sql -Query @"
IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = 'testdb')
    CREATE DATABASE testdb;
"@
Write-Host "  testdb ready." -ForegroundColor Green

# 3. Create weblogic login + user
Write-Host "`n[3/5] Creating weblogic login and user..."
Invoke-Sql -Query @"
IF NOT EXISTS (SELECT name FROM sys.server_principals WHERE name = 'weblogic')
BEGIN
    CREATE LOGIN weblogic WITH PASSWORD = 'weblogic', CHECK_POLICY = OFF;
END
"@
Invoke-Sql -Database "testdb" -Query @"
IF NOT EXISTS (SELECT name FROM sys.database_principals WHERE name = 'weblogic')
BEGIN
    CREATE USER weblogic FOR LOGIN weblogic;
    ALTER ROLE db_owner ADD MEMBER weblogic;
END
"@
Write-Host "  weblogic login/user ready (db_owner on testdb)." -ForegroundColor Green

# 4. Create PersonTableType TVP (Part 8)
Write-Host "`n[4/5] Creating PersonTableType TVP..."
Invoke-Sql -Database "testdb" -Query @"
IF NOT EXISTS (SELECT * FROM sys.types WHERE name = 'PersonTableType')
    CREATE TYPE dbo.PersonTableType AS TABLE (
        person_id INT,
        user_name NVARCHAR(30),
        first_name NVARCHAR(10),
        last_name NVARCHAR(15),
        years INT
    );
"@
Write-Host "  PersonTableType TVP ready." -ForegroundColor Green

# 5. Verify connectivity
Write-Host "`n[5/5] Verifying weblogic can connect..."
try {
    $result = Invoke-Sqlcmd -ServerInstance $ServerInstance -Database "testdb" `
        -Username "weblogic" -Password "weblogic" -TrustServerCertificate `
        -Query "SELECT DB_NAME() AS db, SUSER_NAME() AS login_name"
    Write-Host "  Connected as $($result.login_name) to $($result.db)." -ForegroundColor Green
} catch {
    Write-Host "  Connection failed: $_" -ForegroundColor Red
    Write-Host "  Make sure TCP/IP is enabled on port 1433." -ForegroundColor Yellow
    exit 1
}

Write-Host "`n=== Setup complete! ===" -ForegroundColor Cyan
Write-Host "Run benchmarks: mvn test -pl benchmarks -f ../pom.xml"
