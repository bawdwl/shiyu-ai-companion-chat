[CmdletBinding()]
param(
    [string]$AvdName = "ShiyuCloud",
    [string]$SystemImage = "system-images;android-35;google_apis;x86_64",
    [string]$SdkRoot = "",
    [int]$BootTimeoutSeconds = 180,
    [switch]$Reinstall,
    [switch]$ColdBoot
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not $SdkRoot) {
    $SdkRoot = Join-Path (Split-Path -Parent (Split-Path -Parent $root)) `
        "outputs\android-toolchain\sdk"
}
$SdkRoot = (Resolve-Path -LiteralPath $SdkRoot).Path
$jdk = (Resolve-Path (Join-Path (Split-Path -Parent (Split-Path -Parent $root)) `
    "outputs\android-toolchain\jdk17\jdk-17.0.20.1+1")).Path

$env:JAVA_HOME = $jdk
$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot
$env:GRADLE_USER_HOME = (Resolve-Path (Join-Path `
    (Split-Path -Parent (Split-Path -Parent $root)) `
    "outputs\android-toolchain\gradle-home-current")).Path
$env:Path = "$jdk\bin;$SdkRoot\platform-tools;$SdkRoot\emulator;$env:Path"

$sdkManager = Join-Path $SdkRoot "cmdline-tools\latest\bin\sdkmanager.bat"
$avdManager = Join-Path $SdkRoot "cmdline-tools\latest\bin\avdmanager.bat"
$emulator = Join-Path $SdkRoot "emulator\emulator.exe"
$emulatorCheck = Join-Path $SdkRoot "emulator\emulator-check.exe"
$adb = Join-Path $SdkRoot "platform-tools\adb.exe"
$proxyUri = $null
if ($env:HTTPS_PROXY -and [Uri]::TryCreate($env:HTTPS_PROXY, [UriKind]::Absolute, [ref]$proxyUri)) {
    if (-not $proxyUri.Host -or $proxyUri.Port -le 0) {
        $proxyUri = $null
    }
}
$sdkProxyArgs = @()
if ($proxyUri) {
    $sdkProxyArgs = @(
        "--proxy=http",
        "--proxy_host=$($proxyUri.Host)",
        "--proxy_port=$($proxyUri.Port)"
    )
}

function Ensure-SdkPackage {
    param([string]$Package)
    & $sdkManager --sdk_root=$SdkRoot @sdkProxyArgs $Package
    if ($LASTEXITCODE -ne 0) {
        throw "Android SDK package install failed: $Package"
    }
}

if (-not (Test-Path -LiteralPath $emulator)) {
    Ensure-SdkPackage "emulator"
}
if (-not (Test-Path -LiteralPath (Join-Path $SdkRoot ($SystemImage -replace ';', '\')))) {
    Ensure-SdkPackage $SystemImage
}

$acceleration = (& $emulatorCheck accel 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or $acceleration -notmatch "is installed and usable") {
    throw @"
Android Emulator hardware acceleration is unavailable.

Enable Windows Hypervisor Platform in Windows Features, or install the Android
Emulator Hypervisor Driver from:
$SdkRoot\extras\google\Android_Emulator_Hypervisor_Driver

Run silent_install.bat as administrator, then rerun this script.
"@
}

$avds = @(& $emulator -list-avds)
if ($AvdName -notin $avds) {
    "no" | & $avdManager create avd `
        --name $AvdName `
        --package $SystemImage `
        --device "pixel_6" `
        --force
    if ($LASTEXITCODE -ne 0) {
        throw "AVD creation failed: $AvdName"
    }
}

function Get-EmulatorSerials {
    @(
        & $adb devices |
            ForEach-Object {
                if ($_ -match "^(emulator-\d+)\s+device$") {
                    $matches[1]
                }
            }
    )
}

$existingEmulators = @(Get-EmulatorSerials)
if ($existingEmulators.Count -eq 0) {
    $arguments = @(
        "-avd", $AvdName,
        "-gpu", "auto",
        "-netdelay", "none",
        "-netspeed", "full"
    )
    if ($ColdBoot) {
        $arguments += "-no-snapshot-load"
    }
    if ($proxyUri) {
        $arguments += @("-http-proxy", $proxyUri.AbsoluteUri.TrimEnd("/"))
    }
    Start-Process -FilePath $emulator -ArgumentList $arguments
}

$deadline = (Get-Date).AddSeconds($BootTimeoutSeconds)
$emulatorSerial = $null
do {
    Start-Sleep -Seconds 2
    $emulators = @(Get-EmulatorSerials)
    $emulatorSerial = $emulators |
        Where-Object { $_ -notin $existingEmulators } |
        Select-Object -First 1
    if (-not $emulatorSerial) {
        $emulatorSerial = $emulators | Select-Object -First 1
    }
    if ($emulatorSerial) {
        $booted = (& $adb -s $emulatorSerial shell getprop sys.boot_completed 2>$null).Trim()
        if ($booted -eq "1") {
            break
        }
    }
} while ((Get-Date) -lt $deadline)

if ($booted -ne "1") {
    throw "Android emulator did not finish booting within $BootTimeoutSeconds seconds."
}

& .\android\gradlew.bat -p .\android :app:assembleEmulatorDebug --no-daemon
if ($LASTEXITCODE -ne 0) {
    throw "Emulator APK build failed."
}

$apk = (Resolve-Path `
    "android\app\build\outputs\apk\emulator\debug\app-emulator-debug.apk").Path
$installArgs = @("install", "-r", "-d", $apk)
if ($Reinstall) {
    & $adb -s $emulatorSerial uninstall "com.shiyu.chat.emulator" | Out-Null
    $installArgs = @("install", $apk)
}
& $adb -s $emulatorSerial @installArgs
if ($LASTEXITCODE -ne 0) {
    throw "APK install to emulator failed."
}

& $adb -s $emulatorSerial shell am start `
    -n "com.shiyu.chat.emulator/com.shiyu.chat.MainActivity" | Out-Null
Write-Host "Emulator APK installed and launched."
Write-Host "Device  : $emulatorSerial"
Write-Host "Package : com.shiyu.chat.emulator"
Write-Host "APK     : $apk"
