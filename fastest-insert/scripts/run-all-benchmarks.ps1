<#
.SYNOPSIS
    Runs ALL fastest-insert benchmark suites sequentially.

.DESCRIPTION
    Requires SQL Server running on localhost:1433 with testdb + weblogic user.

.PARAMETER Suite
    Which suite to run: Core, Advanced, Extended, or All (default).
    - Core:     Parts 1-5, 8 (~56 min)
    - Advanced: Thread + Index sweep, Parts 6-7 (~14 min)
    - Extended: 16-thread, 1M, useBulkCopy=true (~7 min)
    - All:      All three suites (~77 min)

.EXAMPLE
    .\run-all-benchmarks.ps1
    .\run-all-benchmarks.ps1 -Suite Core
#>
param(
    [ValidateSet("Core", "Advanced", "Extended", "All")]
    [string]$Suite = "All"
)

$ErrorActionPreference = "Stop"
$projectDir = Split-Path -Parent $PSScriptRoot

Set-Location $projectDir

Write-Host "=== Fastest-Insert Benchmark Suite ===" -ForegroundColor Cyan
Write-Host "Project: $projectDir"
Write-Host "Java:    $(java -version 2>&1 | Select-Object -First 1)"
Write-Host ""

# Build
Write-Host "[Build] Compiling all modules..." -ForegroundColor Yellow
mvn clean install -DskipTests -q
Write-Host "[Build] Done.`n" -ForegroundColor Green

function Invoke-Suite {
    param([string]$Name, [string]$TestClass, [string]$Estimate)
    Write-Host ("=" * 64) -ForegroundColor Cyan
    Write-Host "[Suite] $Name (estimated: $Estimate)" -ForegroundColor Cyan
    Write-Host ("=" * 64) -ForegroundColor Cyan
    mvn test -pl benchmarks "-Dtest=$TestClass" -Dsurefire.useFile=false
    Write-Host ""
}

switch ($Suite) {
    "Core"     { Invoke-Suite "Core Benchmarks (Parts 1-5, 8)" "FastestInsertApplicationTests" "~56 min" }
    "Advanced" { Invoke-Suite "Thread + Index Sweep (Parts 6-7)" "AdvancedBenchmarkTests" "~14 min" }
    "Extended" { Invoke-Suite "16-Thread, 1M Dataset, BulkCopy=true" "ExtendedBenchmarkTests" "~7 min" }
    "All" {
        Invoke-Suite "Core Benchmarks (Parts 1-5, 8)" "FastestInsertApplicationTests" "~56 min"
        Invoke-Suite "Thread + Index Sweep (Parts 6-7)" "AdvancedBenchmarkTests" "~14 min"
        Invoke-Suite "16-Thread, 1M Dataset, BulkCopy=true" "ExtendedBenchmarkTests" "~7 min"
    }
}

Write-Host "`n=== All benchmarks complete ===" -ForegroundColor Cyan
Write-Host "Results stored in testdb.test_result table."
Write-Host "Query: SELECT method_name, batch_size, AVG(duration_ms) FROM test_result GROUP BY method_name, batch_size ORDER BY AVG(duration_ms)"
