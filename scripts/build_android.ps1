param(
    [ValidateSet("full", "cloud")]
    [string]$Edition = "full"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$parent = Split-Path -Parent $root
$grandparent = Split-Path -Parent $parent
$toolchainCandidates = @(
    (Join-Path $root "outputs/android-toolchain"),
    (Join-Path $parent "outputs/android-toolchain"),
    (Join-Path $grandparent "outputs/android-toolchain")
)
$toolchain = $toolchainCandidates |
    Where-Object { Test-Path -LiteralPath $_ } |
    Select-Object -First 1
if (-not $toolchain) {
    $toolchain = $toolchainCandidates[0]
}

if (-not $env:JAVA_HOME) {
    $localJdk = Join-Path $toolchain "jdk17/jdk-17.0.20.1+1"
    if (Test-Path (Join-Path $localJdk "bin/java.exe")) {
        $env:JAVA_HOME = $localJdk
    }
}

if (-not $env:ANDROID_HOME -and $env:ANDROID_SDK_ROOT) {
    $env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
}
if (-not $env:ANDROID_HOME) {
    $localSdk = Join-Path $toolchain "sdk"
    if (Test-Path (Join-Path $localSdk "platforms/android-35/android.jar")) {
        $env:ANDROID_HOME = $localSdk
    }
}

if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin/java.exe"))) {
    throw "JDK 17 未配置。请设置 JAVA_HOME，或安装到 $toolchain\jdk17。"
}
if (-not $env:ANDROID_HOME -or -not (Test-Path (Join-Path $env:ANDROID_HOME "platforms/android-35/android.jar"))) {
    throw "Android SDK 35 未配置。请设置 ANDROID_HOME，或安装到 $toolchain\sdk。"
}

if (-not $env:GRADLE_USER_HOME -and (Test-Path $toolchain)) {
    $env:GRADLE_USER_HOME = Join-Path $toolchain "gradle-home-current"
}
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$task = if ($Edition -eq "cloud") { "assembleCloudDebug" } else { "assembleFullDebug" }
& (Join-Path $root "android/gradlew.bat") -p (Join-Path $root "android") $task --no-daemon
if ($LASTEXITCODE -ne 0) {
    throw "Android 构建失败"
}

$apk = Join-Path $root "android/app/build/outputs/apk/$Edition/debug/app-$Edition-debug.apk"
Get-Item $apk
Get-FileHash $apk -Algorithm SHA256
