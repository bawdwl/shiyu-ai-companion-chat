# 将 Android APK 发布到 GitHub Release

更新日期：2026-11-08

APK 不进入 Git。正确流程是先把源码和版本提交推送，再由 GitHub Release
创建标签并上传 APK。

## 1. 先确认版本和文件

检查：

```text
android/app/build.gradle.kts
├─ versionCode
└─ versionName

release/apk-<version>/
└─ Shiyu-<version>-Android-arm64-v8a.apk
```

版本必须与 APK 文件名一致。例如：

```powershell
Select-String android\app\build.gradle.kts -Pattern "versionCode|versionName"
Get-FileHash release\apk-0.18.23\Shiyu-0.18.23-Android-arm64-v8a.apk -Algorithm SHA256
```

## 2. 测试 APK

```powershell
$env:JAVA_HOME='C:\Users\31153\Documents\Qwen3 IM Chat\outputs\android-toolchain\jdk17\jdk-17.0.20.1+1'
$env:ANDROID_HOME='C:\Users\31153\Documents\Qwen3 IM Chat\outputs\android-toolchain\sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
$env:GRADLE_USER_HOME='C:\Users\31153\Documents\Qwen3 IM Chat\outputs\android-toolchain\gradle-home-current'
$env:Path="$env:JAVA_HOME\bin;$env:Path"

cd android
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug --no-daemon --offline
```

设备在线时覆盖安装，不使用清除数据：

```powershell
C:\Users\31153\Documents\Qwen3 IM Chat\outputs\platform-tools\adb.exe install -r -d <apk>
```

## 3. 提交并推送源码

GitHub Release 只能以 GitHub 已经存在的提交为标签目标：

```powershell
git add .
git commit -m "Release Android 0.18.23"
git push origin main
```

不要只推送标签而漏掉分支提交。Release API 会根据 `target_commitish`
创建远端标签。

## 4. 准备 Release 说明

建议保存为：

```text
release/notes-v0.18.23.md
```

内容写清：

- 用户能看到的变化；
- 修复；
- 已知限制；
- 测试与真机验证状态；
- 是否包含用户数据。

## 5. 先做无网络预检

```powershell
.\scripts\publish_android_release.ps1 `
  -Version 0.18.23 `
  -NotesFile release\notes-v0.18.23.md `
  -WhatIf
```

脚本会检查：

- Gradle 版本；
- 当前分支；
- APK 是否存在；
- APK 文件名；
- SHA-256 和文件大小；
- origin 是否是 GitHub 仓库。

`-WhatIf` 不读取凭据，也不访问 GitHub。

## 6. 正式上传

```powershell
.\scripts\publish_android_release.ps1 `
  -Version 0.18.23 `
  -NotesFile release\notes-v0.18.23.md
```

脚本执行：

1. 从本机 Git Credential Manager 读取 GitHub 凭据，不打印 Token；
2. 若本地已有版本标签，以标签指向的提交为 Release 目标，否则使用当前 `HEAD`；
3. 验证标签目标提交已经在 GitHub；
4. 查询 `v0.18.23` Release 是否存在；
5. 不存在则创建；
6. 以流式方式上传约 286 MiB 的 APK；
7. 再次读取 Release，核对资产大小和下载地址。

如果在 Codex 中执行，网络访问可能要求命令审批；这是正常的安全边界。

## 7. 验证

打开脚本最后输出的 Release URL，并确认：

- 版本号正确；
- APK 为 `arm64-v8a`；
- 文件大小与本地一致；
- SHA-256 与发布说明一致；
- 不是 Draft；
- 没有上传聊天数据库、API Key、个人图片或录音。

## 8. 如果机器安装了 GitHub CLI

也可以使用：

```powershell
gh auth status
gh release create v0.18.23 `
  release\apk-0.18.23\Shiyu-0.18.23-Android-arm64-v8a.apk `
  --title "拾语 Android 0.18.23" `
  --notes-file release\notes-v0.18.23.md
```

但这台机器当前没有可用的 `gh`，所以本仓库默认使用
`scripts/publish_android_release.ps1`。

## 9. 已存在同名资产时

脚本不会自动覆盖已发布 APK。若内容不同：

1. 在 GitHub Release 页面确认是否真的要替换；
2. 用户明确同意后删除旧资产；
3. 重新运行脚本。

不要用同一版本号上传内容不同的 APK；正确做法是提升 `versionCode` 和
`versionName` 后重新发布。
