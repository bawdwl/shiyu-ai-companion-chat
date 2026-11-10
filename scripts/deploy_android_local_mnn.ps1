param(
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $root "outputs/platform-tools/adb.exe"
$apk = Join-Path $root "android/app/build/outputs/apk/full/debug/app-full-debug.apk"
$modelId = "qwen3-1.7b-song-zhiyi-v2-half1-mnn-3.5.0-q8b64"
$modelZip = Join-Path $root "outputs/models/$modelId.zip"
$remoteZip = "/sdcard/Download/$modelId.zip"

if (-not (Test-Path -LiteralPath $adb)) {
    throw "ADB 不存在：$adb"
}
if (-not $SkipBuild) {
    & (Join-Path $PSScriptRoot "build_android.ps1")
}
if (-not (Test-Path -LiteralPath $apk)) {
    throw "APK 不存在：$apk"
}
if (-not (Test-Path -LiteralPath $modelZip)) {
    throw "本地 MNN 模型 ZIP 不存在：$modelZip"
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

& $adb push $modelZip $remoteZip
if ($LASTEXITCODE -ne 0) {
    throw "推送模型 ZIP 失败"
}

& $adb shell am force-stop com.shiyu.chat
& $adb shell monkey -p com.shiyu.chat -c android.intent.category.LAUNCHER 1
if ($LASTEXITCODE -ne 0) {
    throw "拾语启动失败"
}

& $adb shell dumpsys package com.shiyu.chat |
    Select-String "versionCode=|versionName="
Write-Host "完整版 APK 已安装，模型 ZIP 已推送到手机 Download 目录。"
Write-Host "请在拾语设置中选择导入本地 MNN 模型，并选择 $remoteZip。"
Write-Host "不要把模型文件直接用 adb 写入 Android/data，应用会无法读取 shell 创建的目录。"
