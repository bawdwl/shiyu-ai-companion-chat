param(
    [string]$Python = "python",
    [string]$Target = ""
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
if ([string]::IsNullOrWhiteSpace($Target)) {
    $Target = Join-Path $root "outputs\secret-runtime"
}
$Target = [System.IO.Path]::GetFullPath($Target)

& $Python --version
if ($LASTEXITCODE -ne 0) {
    throw "Python is not available: $Python"
}

New-Item -ItemType Directory -Path $Target -Force | Out-Null
& $Python -m pip install --upgrade --target $Target cryptography
if ($LASTEXITCODE -ne 0) {
    throw "cryptography installation failed with code $LASTEXITCODE"
}

& $Python -c "import sys; sys.path.insert(0, r'$Target'); from cryptography.fernet import Fernet; print('secret-runtime-ok')"
if ($LASTEXITCODE -ne 0) {
    throw "secret runtime import check failed with code $LASTEXITCODE"
}
