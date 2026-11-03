param(
    [string]$Python = "python",
    [switch]$Install,
    [ValidateSet("api", "full")]
    [string]$InstallProfile = "api",
    [switch]$SkipTests,
    [switch]$SkipFrontend,
    [string]$ReportRoot = ""
)

$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $false
Set-StrictMode -Version Latest

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location -LiteralPath $ProjectRoot

if ([string]::IsNullOrWhiteSpace($ReportRoot)) {
    $ReportRoot = Join-Path $ProjectRoot (
        "outputs\clean-windows-verification\" +
        (Get-Date -Format "yyyyMMdd-HHmmss")
    )
}

$ReportRoot = [System.IO.Path]::GetFullPath($ReportRoot)
New-Item -ItemType Directory -Path $ReportRoot -Force | Out-Null
$Logs = Join-Path $ReportRoot "logs"
New-Item -ItemType Directory -Path $Logs -Force | Out-Null

$Steps = [System.Collections.Generic.List[object]]::new()

function Invoke-LoggedStep {
    param(
        [string]$Name,
        [scriptblock]$Action
    )

    $started = Get-Date
    $logPath = Join-Path $Logs (($Name -replace "[^A-Za-z0-9_.-]", "_") + ".log")
    $output = @()
    try {
        $previousErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            $output = & $Action 2>&1
        } finally {
            $ErrorActionPreference = $previousErrorActionPreference
        }
        $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
        $output | Tee-Object -FilePath $logPath | Out-Host
        if ($exitCode -ne 0) {
            throw "$Name exited with code $exitCode"
        }
        $Steps.Add([ordered]@{
            name = $Name
            status = "passed"
            exit_code = $exitCode
            seconds = [math]::Round(((Get-Date) - $started).TotalSeconds, 3)
            log = "logs/$([System.IO.Path]::GetFileName($logPath))"
        })
    }
    catch {
        if ($null -ne $output) {
            $output | Tee-Object -FilePath $logPath -Append | Out-Host
        }
        $Steps.Add([ordered]@{
            name = $Name
            status = "failed"
            exit_code = if ($null -eq $LASTEXITCODE) { 1 } else { $LASTEXITCODE }
            seconds = [math]::Round(((Get-Date) - $started).TotalSeconds, 3)
            log = "logs/$([System.IO.Path]::GetFileName($logPath))"
            error = $_.Exception.Message
        })
        throw
    }
}

try {
    $requirementsFile = if ($InstallProfile -eq "full") {
        "requirements-lock-windows-py311.txt"
    } else {
        "requirements-api-windows-py311.txt"
    }
    if (-not (Test-Path -LiteralPath (Join-Path $ProjectRoot $requirementsFile))) {
        throw "$requirementsFile is missing"
    }

    Invoke-LoggedStep "python-version" {
        & $Python --version
    }

    if ($Install) {
        Invoke-LoggedStep "python-install-lock" {
            & $Python -m pip install -r $requirementsFile
        }
        Invoke-LoggedStep "secret-runtime-install" {
            & .\scripts\setup_secret_runtime.ps1 -Python $Python
        }
    }

    Invoke-LoggedStep "python-dependency-check" {
        & $Python -m pip check
    }

    Invoke-LoggedStep "python-import-check" {
        & $Python -B -c "import fastapi, httpx, pydantic, PIL, uvicorn; print('imports-ok')"
    }

    Invoke-LoggedStep "secret-runtime-import-check" {
        & $Python -B -c "import sys; sys.path.insert(0, r'outputs/secret-runtime'); from cryptography.fernet import Fernet; print('secret-runtime-ok')"
    }

    if (-not $SkipTests) {
        Invoke-LoggedStep "python-tests" {
            & $Python -B scripts/check_project.py
        }
        Invoke-LoggedStep "frontend-state-tests" {
            node --test tests/frontend_state.test.cjs
        }
    }

    if (-not $SkipFrontend) {
        Invoke-LoggedStep "node-version" {
            node --version
        }
        Invoke-LoggedStep "pnpm-version" {
            pnpm --version
        }
        if ($Install) {
            Invoke-LoggedStep "frontend-install-lock" {
                pnpm --dir frontend install --frozen-lockfile
            }
        }
        Invoke-LoggedStep "frontend-build" {
            pnpm --dir frontend build
        }
    }

    Invoke-LoggedStep "isolated-api-first-use" {
        & $Python -B scripts/clean_windows_smoke.py --report (
            Join-Path $ReportRoot "first-use-report.json"
        )
    }

    $report = [ordered]@{
        schema_version = 1
        status = "passed"
        created_at = (Get-Date).ToString("o")
        project_root = $ProjectRoot
        python = $Python
        install_requested = [bool]$Install
        install_profile = $InstallProfile
        tests_requested = -not [bool]$SkipTests
        frontend_requested = -not [bool]$SkipFrontend
        steps = $Steps
        limitations = @(
            "This script verifies the current host using an isolated temporary database.",
            "It does not prove behavior on a different, freshly installed computer.",
            "The deterministic first-use smoke does not call a paid cloud API.",
            "A real user should still configure a valid API key and run one first chat manually."
        )
    }
}
catch {
    $report = [ordered]@{
        schema_version = 1
        status = "failed"
        created_at = (Get-Date).ToString("o")
        project_root = $ProjectRoot
        python = $Python
        install_requested = [bool]$Install
        install_profile = $InstallProfile
        tests_requested = -not [bool]$SkipTests
        frontend_requested = -not [bool]$SkipFrontend
        steps = $Steps
        error = $_.Exception.Message
    }
}

$reportPath = Join-Path $ReportRoot "report.json"
$report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $reportPath -Encoding utf8

$markdown = @(
    "# Windows clean-environment verification",
    "",
    "- Status: $($report.status)",
    "- Created: $($report.created_at)",
    "- Python: $Python",
    "- Install requested: $([bool]$Install)",
    "- Install profile: $InstallProfile",
    "- Tests requested: $(-not [bool]$SkipTests)",
    "- Frontend requested: $(-not [bool]$SkipFrontend)",
    "",
    "| Step | Status | Exit | Seconds |",
    "|---|---|---:|---:|"
)
foreach ($step in $Steps) {
    $markdown += "| $($step.name) | $($step.status) | $($step.exit_code) | $($step.seconds) |"
}
$markdown += @(
    "",
    "This run used the current Windows host. A genuine new-machine acceptance still requires running this script after creating a new environment and installing dependencies.",
    ""
)
$markdown | Set-Content -LiteralPath (Join-Path $ReportRoot "report.md") -Encoding utf8

Write-Output ""
Write-Output "Verification report: $reportPath"
if ($report.status -ne "passed") {
    exit 1
}
