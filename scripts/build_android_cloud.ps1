param()

$ErrorActionPreference = "Stop"
& (Join-Path $PSScriptRoot "build_android.ps1") -Edition cloud
