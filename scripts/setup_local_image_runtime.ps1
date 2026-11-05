param(
    [string]$Python = "python",
    [string]$Target = ""
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
if ([string]::IsNullOrWhiteSpace($Target)) {
    $Target = Join-Path $root "outputs\image-runtime"
}
$Target = [System.IO.Path]::GetFullPath($Target)
$model = Join-Path $root "outputs\models\stable-diffusion-v1-5"

& $Python --version
if ($LASTEXITCODE -ne 0) {
    throw "Python is not available: $Python"
}

New-Item -ItemType Directory -Path $Target -Force | Out-Null
& $Python -m pip install --upgrade --target $Target `
    "diffusers==0.42.1" `
    "accelerate==1.15.0"
if ($LASTEXITCODE -ne 0) {
    throw "Local image runtime installation failed: $LASTEXITCODE"
}

& $Python -c "import sys; sys.path.append(r'$Target'); import diffusers, accelerate; print('image-runtime-ok')"
if ($LASTEXITCODE -ne 0) {
    throw "Local image runtime import check failed"
}

Write-Host "Runtime ready: $Target"
Write-Host "Place a local Diffusers SD1.5 directory at: $model"
Write-Host "Then select that model directory in 拾语 settings."
