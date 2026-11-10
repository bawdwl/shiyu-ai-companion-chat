param(
    [string]$ModelZip = "",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $root "outputs/platform-tools/adb.exe"
$apk = Join-Path $root "android/app/build/outputs/apk/full/debug/app-full-debug.apk"
if ([string]::IsNullOrWhiteSpace($ModelZip)) {
    $ModelZip = Join-Path $root "outputs/local-dream-dreamshaper-test/MeinaMixV12_qnn2.28_8gen2.zip"
}
$remoteZip = "/sdcard/Download/MeinaMixV12_qnn2.28_8gen2.zip"

if (-not (Test-Path -LiteralPath $adb)) {
    throw "ADB 不存在：$adb"
}
if (-not $SkipBuild) {
    & (Join-Path $PSScriptRoot "build_android.ps1")
}
if (-not (Test-Path -LiteralPath $apk)) {
    throw "APK 不存在：$apk"
}
if (-not (Test-Path -LiteralPath $ModelZip)) {
    throw "Dream SD1.5 QNN 模型 ZIP 不存在：$ModelZip"
}

$devices = & $adb devices
$online = $devices | Select-String "`tdevice$"
if (-not $online) {
    throw "没有已授权且在线连接的 Android 设备"
}

& $adb install -r -d $apk
if ($LASTEXITCODE -ne 0) {
    throw "APK 覆盖安装失败"
}

& $adb push $ModelZip $remoteZip
if ($LASTEXITCODE -ne 0) {
    throw "推送 Dream 模型 ZIP 失败"
}

& $adb shell am force-stop com.shiyu.chat
& $adb shell monkey -p com.shiyu.chat -c android.intent.category.LAUNCHER 1
if ($LASTEXITCODE -ne 0) {
    throw "拾语启动失败"
}

& $adb shell dumpsys package com.shiyu.chat |
    Select-String "versionCode=|versionName="
Write-Host "完整版 APK 已安装，MeinaMix V12 ZIP 已推送到手机 Download 目录。"
Write-Host "请在拾语“应用设置 → Dream 本地生图”中选择 $remoteZip 导入。"
