# 将 Android APK 发布到 GitHub Release

更新日期：2026-11-09

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
Get-FileHash release\apk-0.19.0\Shiyu-0.19.0-Android-arm64-v8a.apk -Algorithm SHA256
Get-FileHash release\apk-0.19.0-cloud\Shiyu-0.19.0-cloud-Android-arm64-v8a.apk -Algorithm SHA256
```

## 2. 测试 APK

```powershell
$env:JAVA_HOME='C:\Users\31153\Documents\Qwen3 IM Chat\outputs\android-toolchain\jdk17\jdk-17.0.20.1+1'
$env:ANDROID_HOME='C:\Users\31153\Documents\Qwen3 IM Chat\outputs\android-toolchain\sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
$env:GRADLE_USER_HOME='C:\Users\31153\Documents\Qwen3 IM Chat\outputs\android-toolchain\gradle-home-current'
$env:Path="$env:JAVA_HOME\bin;$env:Path"

cd android
.\gradlew.bat testFullDebugUnitTest assembleFullDebug lintFullDebug --no-daemon --offline
.\gradlew.bat testCloudDebugUnitTest assembleCloudDebug lintCloudDebug --no-daemon --offline
```

设备在线时覆盖安装，不使用清除数据：

```powershell
C:\Users\31153\Documents\Qwen3 IM Chat\outputs\platform-tools\adb.exe install -r -d <apk>
```

## 3. 提交并推送源码

GitHub Release 只能以 GitHub 已经存在的提交为标签目标：

```powershell
git add .
git commit -m "Release Android 0.19.0"
git push origin main
```

不要只推送标签而漏掉分支提交。Release API 会根据 `target_commitish`
创建远端标签。

## 4. 准备 Release 说明

建议保存为：

```text
release/notes-v0.19.0.md
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
  -Version 0.19.0 `
  -NotesFile release\notes-v0.19.0.md `
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
  -Version 0.19.0 `
  -NotesFile release\notes-v0.19.0.md
```

脚本执行：

1. 从本机 Git Credential Manager 读取 GitHub 凭据，不打印 Token；
2. 若本地已有版本标签，以标签指向的提交为 Release 目标，否则使用当前 `HEAD`；
3. 验证标签目标提交已经在 GitHub；
4. 查询 `v0.19.0` Release 是否存在；
5. 不存在则创建；
6. 以流式方式上传指定的 APK；
7. 再次读取 Release，核对资产大小和下载地址。

同一版本需要发布完整版和云端版时，先上传完整版，再用
`-ApkPath release\apk-0.19.0-cloud\Shiyu-0.19.0-cloud-Android-arm64-v8a.apk`
运行一次脚本。第二次会复用同一 Release 并追加云端 APK。

如果在 Codex 中执行，网络访问可能要求命令审批；这是正常的安全边界。
脚本会自动定位 Git，并在自身进程内默认绕过环境中的
`HTTP_PROXY`、`HTTPS_PROXY` 和 `ALL_PROXY`，让 GitHub 直连。
只有本机必须经过代理才能访问 GitHub 时，才添加 `-UseEnvironmentProxy`。
也可以用 `-GitExe` 显式指定 `git.exe`。

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
gh release create v0.19.0 `
  release\apk-0.19.0\Shiyu-0.19.0-Android-arm64-v8a.apk `
  release\apk-0.19.0-cloud\Shiyu-0.19.0-cloud-Android-arm64-v8a.apk `
  --title "拾语 Android 0.19.0 · 云端精简版与八角色初始库" `
  --notes-file release\notes-v0.19.0.md
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

## 10. Git 网络与登录恢复

2026-11-08 排查确认，旧发布流程不是 APK 上传本身慢，而是同时遇到两个环境问题：

- Git Credential Manager 中没有可用的 GitHub 账户凭据。交互式补登录会在后台
  等待授权窗口，看起来像命令卡住。
- 用户环境把 Git 流量指向 `127.0.0.1:7890` 的本机代理。该代理路径出现 TLS
  握手失败，而 GitHub 直连返回 `200`。

当前脚本已经处理这两点：

- 子进程默认移除 `HTTP_PROXY`、`HTTPS_PROXY`、`ALL_PROXY` 及小写同名变量，
  GitHub API、上传接口和 Git 查询都走直连。
- 设置 `GCM_INTERACTIVE=Never`，凭据缺失时立即失败，不再无限等待登录窗口。
- `HttpClientHandler.UseProxy` 也会关闭，避免上传接口重新读取系统代理。
- 自动查找 Git for Windows、用户目录 Git 或本机 Codex Git 运行时。

首次登录或凭据失效时运行：

```powershell
git-credential-manager github login `
  --device `
  --no-ui `
  --username bawdwl `
  --force
```

浏览器完成设备授权后，如果 GitHub 要求 sudo 密码确认，需要由账户本人输入。
Token 只写入 Windows Credential Manager，不写入仓库或脚本输出。

不在发布脚本内执行普通 `git push` 时，可以永久为本仓库设置直连并关闭不需要的
LFS 锁验证：

```powershell
git config --local http.https://github.com.proxy ""
git config --local `
  lfs.https://github.com/bawdwl/shiyu-ai-companion-chat.git/info/lfs.locksverify `
  false
```

验证凭据只检查是否存在，不输出 Token：

```powershell
$credential = @("protocol=https", "host=github.com", "", "") |
  git -c credential.interactive=never credential fill
[bool]($credential | Where-Object { $_ -like "password=*" })
```
