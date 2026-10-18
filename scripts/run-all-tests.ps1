<#
.SYNOPSIS
    Runs tests for ALL blog-examples projects.

.PARAMETER SkipDb
    Skip fastest-insert (requires SQL Server).

.PARAMETER Project
    Run only a specific project (e.g. "extract-zip-stream").

.EXAMPLE
    .\run-all-tests.ps1
    .\run-all-tests.ps1 -SkipDb
    .\run-all-tests.ps1 -Project extract-zip-stream
#>
param(
    [switch]$SkipDb,
    [string]$Project = ""
)

$ErrorActionPreference = "Continue"
$repoRoot = Split-Path -Parent $PSScriptRoot

$pass = 0; $fail = 0; $skip = 0

function Invoke-ProjectTest {
    param([string]$Dir, [string]$Name, [bool]$NeedsDb = $false)

    if ($Project -and $Project -ne $Name) { return }
    if ($NeedsDb -and $SkipDb) {
        Write-Host "[SKIP] $Name (requires SQL Server, -SkipDb set)" -ForegroundColor Yellow
        $script:skip++
        return
    }

    Write-Host ("=" * 64) -ForegroundColor Cyan
    Write-Host "[TEST] $Name" -ForegroundColor Cyan
    Write-Host ("=" * 64) -ForegroundColor Cyan

    Push-Location "$repoRoot\$Dir"
    try {
        mvn clean test -q
        if ($LASTEXITCODE -eq 0) {
            Write-Host "[PASS] $Name" -ForegroundColor Green
            $script:pass++
        } else {
            Write-Host "[FAIL] $Name" -ForegroundColor Red
            $script:fail++
        }
    } finally {
        Pop-Location
    }
    Write-Host ""
}

Write-Host "=== Blog Examples - Test Suite ===" -ForegroundColor Cyan
Write-Host ""

Invoke-ProjectTest "extract-zip-stream"     "extract-zip-stream"
Invoke-ProjectTest "junit5-parallel-tests"  "junit5-parallel-tests"
Invoke-ProjectTest "data_to_functions"      "data_to_functions"
Invoke-ProjectTest "fastest-insert"         "fastest-insert" $true

Write-Host "`n=== Results ===" -ForegroundColor Cyan
Write-Host "Pass: $pass  Fail: $fail  Skip: $skip"

if ($fail -gt 0) { exit 1 }
