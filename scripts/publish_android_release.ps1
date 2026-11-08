[CmdletBinding()]
param(
    [string]$Version,
    [string]$ApkPath,
    [string]$NotesFile,
    [string]$Title,
    [string]$TargetBranch = "main",
    [switch]$Draft,
    [switch]$Prerelease,
    [switch]$WhatIf
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not $Version) {
    $gradle = Get-Content "android/app/build.gradle.kts" -Raw
    $match = [regex]::Match($gradle, 'versionName\s*=\s*"([^"]+)"')
    if (-not $match.Success) {
        throw "无法从 android/app/build.gradle.kts 读取 versionName。"
    }
    $Version = $match.Groups[1].Value
}
$Version = $Version.Trim().TrimStart("v")
$tag = "v$Version"

if (-not $ApkPath) {
    $ApkPath = "release/apk-$Version/Shiyu-$Version-Android-arm64-v8a.apk"
}
$apk = (Resolve-Path -LiteralPath $ApkPath).Path
if ([IO.Path]::GetExtension($apk) -ne ".apk") {
    throw "发布文件必须是 APK：$apk"
}
$apkItem = Get-Item -LiteralPath $apk
$assetName = $apkItem.Name
$sha256 = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash

if (-not $Title) {
    $Title = "拾语 Android $Version"
}
if (-not $NotesFile) {
    $candidate = "release/notes-v$Version.md"
    if (Test-Path -LiteralPath $candidate) {
        $NotesFile = $candidate
    }
}
if ($NotesFile -and -not (Test-Path -LiteralPath $NotesFile)) {
    throw "Release 说明文件不存在：$NotesFile"
}

$remote = git remote get-url origin
if ($LASTEXITCODE -ne 0 -or -not $remote) {
    throw "当前目录没有可用的 origin。"
}
$slug = $remote
if ($slug -match '^https://github\.com/(.+?)(?:\.git)?$') {
    $slug = $Matches[1]
} elseif ($slug -match '^git@github\.com:(.+?)(?:\.git)?$') {
    $slug = $Matches[1]
} else {
    throw "目前只支持 github.com 的 origin：$remote"
}
if ($slug -notmatch '^[^/]+/[^/]+$') {
    throw "无法识别 GitHub 仓库：$slug"
}

$head = (git rev-parse HEAD).Trim()
$targetCommit = $head
$localTagCommit = (git rev-list -n 1 $tag 2>$null)
if ($LASTEXITCODE -eq 0 -and $localTagCommit) {
    $targetCommit = $localTagCommit.Trim()
}
$branch = (git branch --show-current).Trim()
if ($branch -and $branch -ne $TargetBranch) {
    throw "当前分支是 $branch，但目标分支是 $TargetBranch。请先切换或显式修改 -TargetBranch。"
}

$notes = if ($NotesFile) {
    (Get-Content -LiteralPath $NotesFile -Raw).Trim()
} else {
    "Android $Version 发布。"
}
$bodyText = @"
$notes

### 安装包

- 文件：$assetName
- 大小：$($apkItem.Length) 字节
- SHA-256：$sha256
- 适用架构：arm64-v8a
"@

Write-Host "Repository : $slug"
Write-Host "Version    : $Version"
Write-Host "Tag        : $tag"
Write-Host "Branch HEAD: $head"
Write-Host "Tag target : $targetCommit"
Write-Host "APK        : $apk"
Write-Host "Size       : $($apkItem.Length) bytes"
Write-Host "SHA-256    : $sha256"

if ($WhatIf) {
    Write-Host "WhatIf：只验证输入，不读取凭据，不访问 GitHub。"
    return
}

$credential = @("protocol=https", "host=github.com", "", "") | git credential fill 2>$null
$tokenLine = $credential |
    Where-Object { $_ -like "password=*" } |
    Select-Object -First 1
if (-not $tokenLine) {
    throw "未取得 GitHub 凭据。请先让 Git Credential Manager 完成 GitHub 登录。"
}
$token = $tokenLine -replace "^password=", ""
$userLine = $credential |
    Where-Object { $_ -like "username=*" } |
    Select-Object -First 1
$githubUser = if ($userLine) {
    $userLine -replace "^username=", ""
} else {
    "shiyu-release"
}

$headers = @{
    Authorization = "Bearer $token"
    Accept = "application/vnd.github+json"
    "User-Agent" = "shiyu-release-$githubUser"
    "X-GitHub-Api-Version" = "2022-11-28"
}
$api = "https://api.github.com/repos/$slug"

try {
    Invoke-RestMethod -Method Get -Uri "$api/commits/$targetCommit" -Headers $headers | Out-Null
} catch {
    throw "标签目标提交 $targetCommit 尚未出现在 GitHub。请先运行：git push origin $TargetBranch"
}

$release = $null
try {
    $release = Invoke-RestMethod -Method Get -Uri "$api/releases/tags/$tag" -Headers $headers
    Write-Host "Release 已存在：$($release.html_url)"
} catch {
    if ($_.Exception.Response.StatusCode.value__ -ne 404) {
        throw
    }
}

if (-not $release) {
    $payload = @{
        tag_name = $tag
        target_commitish = $targetCommit
        name = $Title
        body = $bodyText
        draft = [bool]$Draft
        prerelease = [bool]$Prerelease
        generate_release_notes = $false
    } | ConvertTo-Json -Depth 5
    $release = Invoke-RestMethod `
        -Method Post `
        -Uri "$api/releases" `
        -Headers $headers `
        -ContentType "application/json; charset=utf-8" `
        -Body $payload
    Write-Host "Release 已创建：$($release.html_url)"
}

$existingAsset = @($release.assets | Where-Object { $_.name -eq $assetName })
if ($existingAsset.Count -gt 0) {
    if ([long]$existingAsset[0].size -eq $apkItem.Length) {
        Write-Host "APK 已存在且大小一致，不重复上传：$($existingAsset[0].browser_download_url)"
        return
    }
    throw "同名 APK 已存在但大小不同。为避免覆盖发布资产，请先在 GitHub 页面确认后手动删除旧资产。"
}

Add-Type -AssemblyName System.Net.Http
$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromMinutes(20)
$client.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $token)
$client.DefaultRequestHeaders.UserAgent.ParseAdd("shiyu-release-$githubUser")
$client.DefaultRequestHeaders.Accept.ParseAdd("application/vnd.github+json")

$stream = $null
$content = $null
$request = $null
$response = $null
try {
    $stream = [IO.File]::OpenRead($apk)
    $content = [System.Net.Http.StreamContent]::new($stream)
    $content.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::new(
        "application/vnd.android.package-archive"
    )
    $escapedName = [Uri]::EscapeDataString($assetName)
    $uploadUri = "https://uploads.github.com/repos/$slug/releases/$($release.id)/assets?name=$escapedName"
    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::Post,
        $uploadUri
    )
    $request.Content = $content
    $response = $client.SendAsync(
        $request,
        [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead
    ).GetAwaiter().GetResult()
    $responseText = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) {
        throw "上传失败：HTTP $([int]$response.StatusCode) $responseText"
    }
    $asset = $responseText | ConvertFrom-Json
    Write-Host "APK 上传完成：$($asset.browser_download_url)"
} finally {
    if ($response) { $response.Dispose() }
    if ($request) { $request.Dispose() }
    if ($content) { $content.Dispose() } elseif ($stream) { $stream.Dispose() }
    $client.Dispose()
}

$final = Invoke-RestMethod -Method Get -Uri "$api/releases/tags/$tag" -Headers $headers
Write-Host "Release URL：$($final.html_url)"
Write-Host "Draft       ：$($final.draft)"
Write-Host "Prerelease  ：$($final.prerelease)"
$final.assets | Select-Object name, size, browser_download_url | Format-Table -AutoSize
